package fi.poltsi.vempain.file.task;

import fi.poltsi.vempain.file.api.TaskStatusEnum;
import fi.poltsi.vempain.file.api.response.TaskAcceptedResponse;
import fi.poltsi.vempain.file.api.response.TaskProgressResponse;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable, thread-safe progress record of one background task. Worker code receives the instance and
 * <ul>
 *   <li>reports its steps through {@link #setTotalSteps(long)}, {@link #advance(String)} and {@link #advanceFailed(String)},</li>
 *   <li>calls {@link #checkpoint()} between units of work so that a cancellation request stops it,</li>
 *   <li>registers an undo action for every change it makes through {@link #registerCompensation(String, Compensation)}.</li>
 * </ul>
 * The {@link TaskRunner} owns the lifecycle transitions and runs the compensations in reverse order when the task is cancelled or
 * fails. The record is deliberately independent of any file backend type so that the facility can be extracted into a shared
 * component.
 */
@Getter
public class TaskProgress {

	private final String  id;
	private final String  type;
	private final String  title;
	private final Long    ownerId;
	private final Instant createdAt = Instant.now();

	private final AtomicLong    totalSteps      = new AtomicLong();
	private final AtomicLong    completedSteps  = new AtomicLong();
	private final AtomicLong    failedSteps     = new AtomicLong();
	private final AtomicLong    revertedSteps   = new AtomicLong();
	private final AtomicBoolean cancelRequested = new AtomicBoolean();

	/**
	 * Undo actions, most recent first. Guarded by {@code this}.
	 */
	private final Deque<RegisteredCompensation> compensations = new ArrayDeque<>();

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

	/**
	 * Registers the undo action of a change the task has just made. Compensations run in reverse registration order when the
	 * task is cancelled or fails.
	 *
	 * @param description  what the undo does, shown to the user while reverting
	 * @param compensation the undo action
	 */
	public void registerCompensation(String description, Compensation compensation) {
		synchronized (this) {
			compensations.push(new RegisteredCompensation(description, compensation));
		}
	}

	/**
	 * Stops the task when the owner has requested cancellation. Workers call this between units of work, before starting a
	 * change that would need its own compensation.
	 *
	 * @throws TaskCancelledException when cancellation was requested
	 */
	public void checkpoint() {
		if (cancelRequested.get()) {
			throw new TaskCancelledException();
		}
	}

	public boolean isCancelRequested() {
		return cancelRequested.get();
	}

	public boolean isFinished() {
		return status.isFinished();
	}

	/**
	 * Asks the task to stop. A queued task is cancelled on the spot by the runner before any work starts; a running task
	 * moves to CANCELLING and stops at its next checkpoint.
	 *
	 * @return false when the task had already finished
	 */
	boolean requestCancel() {
		if (isFinished()) {
			return false;
		}
		cancelRequested.set(true);
		if (status == TaskStatusEnum.RUNNING) {
			status = TaskStatusEnum.CANCELLING;
		}
		return true;
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
		status     = TaskStatusEnum.FAILED;
		finishedAt = Instant.now();
	}

	void cancelled(String finalMessage) {
		message    = finalMessage;
		status     = TaskStatusEnum.CANCELLED;
		finishedAt = Instant.now();
	}

	void reverting() {
		if (status == TaskStatusEnum.RUNNING) {
			status = TaskStatusEnum.CANCELLING;
		}
	}

	/**
	 * Removes and returns the registered compensations, most recent first.
	 */
	List<RegisteredCompensation> drainCompensations() {
		synchronized (this) {
			var drained = List.copyOf(compensations);
			compensations.clear();
			return drained;
		}
	}

	void countReverted() {
		revertedSteps.incrementAndGet();
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
								   .cancelRequested(cancelRequested.get())
								   .revertedSteps(revertedSteps.get())
								   .message(message)
								   .errorMessage(errorMessage)
								   .result(result)
								   .createdAt(createdAt)
								   .startedAt(startedAt)
								   .finishedAt(finishedAt)
								   .build();
	}

	record RegisteredCompensation(String description, Compensation compensation) {
	}
}
