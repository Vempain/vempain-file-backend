package fi.poltsi.vempain.file.schedule;

import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.service.ThumbnailGenerationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateMissingThumbnailsSchedule {

	private final ExportFileRepository       exportFileRepository;
	private final ThumbnailGenerationService thumbnailGenerationService;

	@Value("${vempain.generate-missing-thumbnails.batch-size}")
	private int    batchSize   = 1000;
	@Value("${vempain.generate-missing-thumbnails.enabled:true}")
	private boolean schedulerEnabled;
	@Value("${vempain.generate-missing-thumbnails.worker-count:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}")
	private int    workerCount = Runtime.getRuntime()
	                                    .availableProcessors();
	@Value("${vempain.generate-missing-thumbnails.thumb-image-quality}")
	private float  thumbnailQuality;
	@Value("${vempain.generate-missing-thumbnails.thumb-image-size}")
	private int    thumbnailMinimumSize;
	@Value("${vempain.generate-missing-thumbnails.video-capture-percentage:0.3}")
	private float videoCapturePercentage;
	@Value("${vempain.original-root-directory}")
	private String originalRootDirectory;
	@Value("${vempain.export-root-directory}")
	private String exportRootDirectory;

	private final AtomicBoolean          running = new AtomicBoolean();
	private       ThreadPoolTaskExecutor executor;

	@PostConstruct
	void initializeExecutor() {
		if (executor != null) {
			return;
		}
		executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(workerCount);
		executor.setMaxPoolSize(workerCount);
		executor.setQueueCapacity(Math.max(batchSize, workerCount));
		executor.setThreadNamePrefix("thumbnail-generator-");
		executor.initialize();
	}

	@PreDestroy
	void shutdownExecutor() {
		if (executor != null) {
			executor.shutdown();
		}
	}

	@Scheduled(cron = "${vempain.generate-missing-thumbnails.cron:0 0 * * * *}")
	public void generateMissingThumbnailsScheduled() {
		if (!schedulerEnabled) {
			return;
		}
		generateMissingThumbnails();
	}

	public void generateMissingThumbnails() {
		if (!running.compareAndSet(false, true)) {
			log.warn("Skipping thumbnail generation because a previous invocation is still running");
			return;
		}
		try {
			initializeExecutor();
			var exportFiles = exportFileRepository.findImagesMissingThumbnails(
					org.springframework.data.domain.PageRequest.of(0, batchSize));
			log.info("Generating missing thumbnails for {} files using {} workers",
			         exportFiles.size(), workerCount);
			awaitThumbnailJobs(exportFiles);
		} finally {
			running.set(false);
		}
	}

	private void awaitThumbnailJobs(List<ExportFileEntity> exportFiles) {
		var jobs = new ArrayList<ThumbnailJob>(exportFiles.size());
		for (var exportFile : exportFiles) {
			try {
				var future = executor.submit((Callable<Void>) () -> {
					try {
						thumbnailGenerationService.generateThumbnail(
								exportFile, originalRootDirectory, exportRootDirectory, thumbnailMinimumSize, thumbnailQuality,
								videoCapturePercentage);
					} catch (DataIntegrityViolationException e) {
						log.debug("Thumbnail for export file id={} was created concurrently", exportFile.getId());
					}
					return null;
				});
				jobs.add(new ThumbnailJob(exportFile, future));
			} catch (Exception e) {
				log.error("Failed to submit thumbnail job for export file id={} file={}",
				          exportFile.getId(), exportFile.getFilename(), e);
			}
		}
		for (var job : jobs) {
			try {
				job.future()
				   .get();
			} catch (InterruptedException e) {
				Thread.currentThread()
				      .interrupt();
				log.error("Interrupted while waiting for thumbnail job for export file id={}",
				          job.exportFile()
				             .getId(), e);
			} catch (Exception e) {
				log.error("Thumbnail worker failed for export file id={} file={}",
				          job.exportFile()
				             .getId(), job.exportFile()
				                          .getFilename(), e);
			}
		}
	}

	private record ThumbnailJob(ExportFileEntity exportFile, Future<?> future) {
	}
}
