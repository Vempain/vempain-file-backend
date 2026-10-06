package fi.poltsi.vempain.file.task;

import fi.poltsi.vempain.file.api.TaskStatusEnum;
import fi.poltsi.vempain.file.api.response.TaskAcceptedResponse;
import fi.poltsi.vempain.file.api.response.TaskProgressResponse;
import lombok.Getter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable, thread-safe progress record of one background task. Worker code receives the instance and reports its steps through
 * {@link #setTotalSteps(long)}, {@link #advance(String)} and {@link #advanceFailed(String)}; the {@link TaskRunner} owns the
 * lifecycle transitions. The record is deliberately independent of any file backend type so that the facility can be extracted
 * into a shared component.
 */
@Getter
public class TaskProgress {

	private final String  id;
	private final String  type;
	private final String  title;
	private final Long    ownerId;
	private final Instant createdAt = Instant.now();

	private final AtomicLong totalSteps     = new AtomicLong();
	private final AtomicLong completedSteps = new AtomicLong();
	private final AtomicLong failedSteps    = new AtomicLong();

	private volatile TaskStatusEnum status = TaskStatusEnum.QUEUED;
	private volatile String         message;
	private volatile String         errorMessage;
	private volatile Object         result;
	private volatile Instant        startedAt;
	private volatile Instant        finishedAt;

	TaskProgress(String id, String type, String title, Long ownerId, long totalSteps) {
		this.id      = id;
		this.type    = type;
		this.title   = title;
		this.ownerId = ownerId;
		this.totalSteps.set(Math.max(0, totalSteps));
	}

	/**
	 * Sets or corrects the number of steps once the worker knows it.
	 */
	public void setTotalSteps(long steps) {
		totalSteps.set(Math.max(0, steps));
	}

	/**
	 * Records one successfully finished step.
	 *
	 * @param stepMessage description of the step, shown to the user
	 */
	public void advance(String stepMessage) {
		completedSteps.incrementAndGet();
		message = stepMessage;
	}

	/**
	 * Records one failed step without failing the whole task.
	 *
	 * @param stepMessage description of the failure, shown to the user
	 */
	public void advanceFailed(String stepMessage) {
		completedSteps.incrementAndGet();
		failedSteps.incrementAndGet();
		message = stepMessage;
	}

	/**
	 * Updates the current step description without counting a step.
	 */
	public void message(String stepMessage) {
		message = stepMessage;
	}

	public boolean isFinished() {
		return status.isFinished();
	}

	void start() {
		status    = TaskStatusEnum.RUNNING;
		startedAt = Instant.now();
	}

	void complete(Object taskResult) {
		result     = taskResult;
		status     = TaskStatusEnum.COMPLETED;
		finishedAt = Instant.now();
		if (completedSteps.get() < totalSteps.get()) {
			completedSteps.set(totalSteps.get());
		}
	}

	void fail(String error) {
		errorMessage = error;
		status       = TaskStatusEnum.FAILED;
		finishedAt   = Instant.now();
	}

	public int percent() {
		if (isFinished()) {
			return 100;
		}
		long total = totalSteps.get();
		if (total <= 0) {
			return 0;
		}
		return (int) Math.min(99, Math.round(100.0 * completedSteps.get() / total));
	}

	public TaskAcceptedResponse toAcceptedResponse() {
		return TaskAcceptedResponse.builder()
								   .taskId(id)
								   .type(type)
								   .title(title)
								   .status(status)
								   .totalSteps(totalSteps.get())
								   .build();
	}

	public TaskProgressResponse toResponse() {
		return TaskProgressResponse.builder()
								   .taskId(id)
								   .type(type)
								   .title(title)
								   .status(status)
								   .totalSteps(totalSteps.get())
								   .completedSteps(completedSteps.get())
								   .failedSteps(failedSteps.get())
								   .percent(percent())
								   .message(message)
								   .errorMessage(errorMessage)
								   .result(result)
								   .createdAt(createdAt)
								   .startedAt(startedAt)
								   .finishedAt(finishedAt)
								   .build();
	}
}
