package fi.poltsi.vempain.file.schedule;

import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.service.FileProcessingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueMissingVideoExportsSchedule {
	private final FileRepository             fileRepository;
	private final FileProcessingQueueService queueService;

	@Value("${vempain.queue-missing-video-exports.batch-size:1000}")
	private int     batchSize   = 1000;
	@Value("${vempain.queue-missing-video-exports.enabled:true}")
	private boolean schedulerEnabled;
	@Value("${vempain.queue-missing-video-exports.worker-count:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}")
	private int     workerCount = Runtime.getRuntime()
	                                     .availableProcessors();

	private final AtomicBoolean running = new AtomicBoolean();

	@Scheduled(cron = "${vempain.queue-missing-video-exports.cron:0 0 * * * *}")
	public void queueMissingVideoExportsScheduled() {
		if (!schedulerEnabled) {
			return;
		}

		queueMissingVideoExports();
	}

	public void queueMissingVideoExports() {
		if (!running.compareAndSet(false, true)) {
			log.warn("Skipping missing video export discovery because a previous invocation is still running");
			return;
		}

		try {
			var videos = fileRepository.findVideosMissingExports(PageRequest.of(0, batchSize));
			log.info("Queueing {} video files missing exports using {} workers", videos.size(), workerCount);
			videos.forEach(queueService::enqueueIfNotExported);
		} finally {
			running.set(false);
		}
	}
}
