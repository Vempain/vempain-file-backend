package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.PublishProgressStatusEnum;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PublishProgressStore {

	private final AtomicLong total     = new AtomicLong(0);
	private final AtomicLong scheduled = new AtomicLong(0);
	private final AtomicLong started   = new AtomicLong(0);
	private final AtomicLong completed = new AtomicLong(0);
	private final AtomicLong failed    = new AtomicLong(0);

	private final Map<Long, PublishProgressStatusEnum> perGroup = new ConcurrentHashMap<>();

	@Getter
	private volatile Instant lastUpdated = Instant.now();

	public void init(long totalGroups) {
		total.set(totalGroups);
		scheduled.set(0);
		started.set(0);
		completed.set(0);
		failed.set(0);
		perGroup.clear();
		touch();
	}

	public void markScheduled(long groupId) {
		perGroup.put(groupId, PublishProgressStatusEnum.SCHEDULED);
		scheduled.incrementAndGet();
		touch();
	}

	public void markStarted(long groupId) {
		perGroup.put(groupId, PublishProgressStatusEnum.STARTED);
		started.incrementAndGet();
		touch();
	}

	public void markCompleted(long groupId) {
		perGroup.put(groupId, PublishProgressStatusEnum.COMPLETED);
		completed.incrementAndGet();
		touch();
	}

	public void markFailed(long groupId) {
		perGroup.put(groupId, PublishProgressStatusEnum.FAILED);
		failed.incrementAndGet();
		touch();
	}

	public long getTotal() {
		return total.get();
	}

	public long getScheduled() {
		return scheduled.get();
	}

	public long getStarted() {
		return started.get();
	}

	public long getCompleted() {
		return completed.get();
	}

	public long getFailed() {
		return failed.get();
	}

	public Map<Long, PublishProgressStatusEnum> getPerGroupStatus() {
		return perGroup;
	}

	private void touch() {
		lastUpdated = Instant.now();
	}
}
