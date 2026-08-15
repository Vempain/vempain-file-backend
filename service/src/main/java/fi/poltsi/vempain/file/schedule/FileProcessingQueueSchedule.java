package fi.poltsi.vempain.file.schedule;

import fi.poltsi.vempain.file.entity.FileProcessingQueueEntity;
import fi.poltsi.vempain.file.processing.FileQueueProcessorFactory;
import fi.poltsi.vempain.file.service.FileProcessingQueueService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingQueueSchedule {
	private final FileProcessingQueueService queueService;
	private final FileQueueProcessorFactory  processorFactory;

	@Value("${vempain.file-processing.enabled:true}")
	private boolean enabled;
	@Value("${vempain.file-processing.batch-size:100}")
	private int     batchSize;
	@Value("${vempain.file-processing.worker-count:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}")
	private int     workerCount;

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
		executor.setQueueCapacity(batchSize);
		executor.setThreadNamePrefix("file-processor-");
		executor.initialize();
	}

	@PreDestroy
	void shutdownExecutor() {
		if (executor != null) {
			executor.shutdown();
		}
	}

	@Scheduled(cron = "${vempain.file-processing.cron:0 */15 * * * *}")
	public void processQueueScheduled() {
		if (enabled) {
			log.debug("Scheduled file processing queue triggered");
			processQueue();
		}
	}

	public void processQueue() {
		if (!running.compareAndSet(false, true)) {
			log.warn("Skipping file processing because a previous invocation is still running");
			return;
		}

		try {
			initializeExecutor();
			var items = queueService.claimQueued(batchSize);
			var jobs  = new ArrayList<QueueJob>(items.size());

			for (var item : items) {
				jobs.add(new QueueJob(item, executor.submit(() -> process(item))));
			}

			for (var job : jobs) {
				try {
					job.future()
					   .get();
				} catch (InterruptedException e) {
					Thread.currentThread()
						  .interrupt();
					queueService.fail(job.item(), e);
				} catch (Exception e) {
					queueService.fail(job.item(), e.getCause() instanceof Exception cause ? cause : e);
				}
			}
		} finally {
			running.set(false);
		}
	}

	private void process(FileProcessingQueueEntity item) {
		log.debug("Processing file queue item {} of type {}", item.getId(), item.getFileType());

		try {
			processorFactory.processorFor(item.getFileType())
							.process(item);
			queueService.complete(item);
		} catch (Exception e) {
			log.error("Failed to process file queue item id={} fileId={} type={}",
					  item.getId(), item.getFile()
										.getId(), item.getFileType(), e);
			throw new RuntimeException(e);
		}
	}

	private record QueueJob(FileProcessingQueueEntity item, Future<?> future) {
	}
}
