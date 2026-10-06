package fi.poltsi.vempain.file.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of background tasks. Finished tasks stay available for {@code vempain.tasks.retention-minutes} so that a
 * client can still read the result after a page reload, unless the owner dismisses them earlier.
 */
@Slf4j
@Component
public class TaskProgressStore {

	private final Map<String, TaskProgress> tasks = new ConcurrentHashMap<>();

	@Value("${vempain.scheduling.enabled:true}")
	private boolean schedulingEnabled;

	@Value("${vempain.tasks.retention-minutes:120}")
	private long retentionMinutes;

	public TaskProgress create(String type, String title, Long ownerId, long totalSteps) {
		var progress = new TaskProgress(UUID.randomUUID()
											.toString(), type, title, ownerId, totalSteps);
		tasks.put(progress.getId(), progress);
		return progress;
	}

	public Optional<TaskProgress> find(String taskId) {
		return Optional.ofNullable(taskId == null ? null : tasks.get(taskId));
	}

	/**
	 * Tasks of one owner, newest first.
	 */
	public List<TaskProgress> findByOwner(Long ownerId) {
		return tasks.values()
					.stream()
					.filter(task -> ownerId != null && ownerId.equals(task.getOwnerId()))
					.sorted(Comparator.comparing(TaskProgress::getCreatedAt)
									  .reversed())
					.toList();
	}

	public boolean remove(String taskId) {
		return tasks.remove(taskId) != null;
	}

	public int size() {
		return tasks.size();
	}

	/**
	 * Drops finished tasks older than the retention period.
	 */
	public int evictFinishedBefore(Instant cutoff) {
		var before = tasks.size();
		tasks.values()
			 .removeIf(task -> task.isFinished() && task.getFinishedAt() != null && task.getFinishedAt()
																						.isBefore(cutoff));
		return before - tasks.size();
	}

	@Scheduled(fixedDelayString = "${vempain.tasks.cleanup-interval-ms:60000}")
	public void evictExpired() {
		if (!schedulingEnabled) {
			return;
		}
		var evicted = evictFinishedBefore(Instant.now()
												 .minus(Duration.ofMinutes(retentionMinutes)));
		if (evicted > 0) {
			log.debug("Evicted {} finished background tasks", evicted);
		}
	}
}
