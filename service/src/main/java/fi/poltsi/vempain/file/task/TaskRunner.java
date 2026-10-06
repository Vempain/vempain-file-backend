package fi.poltsi.vempain.file.task;

import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;

/**
 * Runs long-running work as tracked background tasks.
 * <ul>
 *   <li>The task is registered in the {@link TaskProgressStore} immediately so the caller can return its id with HTTP 202.</li>
 *   <li>The submitter's {@link SecurityContext} is propagated to the worker thread, so ACL checks and audit fields keep working.
 *   Authorization that should fail the request itself must still run synchronously before {@code submit}.</li>
 *   <li>When {@code submit} is called inside a transaction the work starts after that transaction has committed, so the worker
 *   never observes uncommitted state of the request.</li>
 * </ul>
 */
@Slf4j
@Service
public class TaskRunner {

	private final TaskProgressStore store;
	private final Executor          executor;
	private final boolean           ownsExecutor;

	@Autowired
	public TaskRunner(TaskProgressStore store, @Value("${vempain.tasks.worker-count:4}") int workerCount) {
		this(store, newExecutor(Math.max(1, workerCount)), true);
	}

	/**
	 * Constructor for tests and for embedding the runner with a caller-provided executor.
	 */
	public TaskRunner(TaskProgressStore store, Executor executor) {
		this(store, executor, false);
	}

	private TaskRunner(TaskProgressStore store, Executor executor, boolean ownsExecutor) {
		this.store        = store;
		this.executor     = executor;
		this.ownsExecutor = ownsExecutor;
	}

	/**
	 * Registers and starts a task owned by the current user.
	 *
	 * @param type       task type identifier, see {@code TaskTypeEnum}
	 * @param title      human readable title
	 * @param totalSteps number of steps when known, otherwise 0 (the work may set it later)
	 * @param work       the work to perform; its return value becomes the task result
	 * @return the tracked progress record, already registered in the store
	 */
	public TaskProgress submit(String type, String title, long totalSteps, TaskWork<?> work) {
		var progress = store.create(type, title, currentUserId(), totalSteps);
		var runnable = new DelegatingSecurityContextRunnable(() -> execute(progress, work), copyOfCurrentSecurityContext());

		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					executor.execute(runnable);
				}

				@Override
				public void afterCompletion(int status) {
					if (status != STATUS_COMMITTED) {
						progress.fail("The request was rolled back before the task could start");
					}
				}
			});
		} else {
			executor.execute(runnable);
		}

		return progress;
	}

	private void execute(TaskProgress progress, TaskWork<?> work) {
		progress.start();
		log.info("Background task {} ({}) started: {}", progress.getId(), progress.getType(), progress.getTitle());
		try {
			var result = work.run(progress);
			progress.complete(result);
			log.info("Background task {} ({}) completed with {}/{} steps, {} failed", progress.getId(), progress.getType(),
					 progress.getCompletedSteps()
							 .get(), progress.getTotalSteps()
											 .get(), progress.getFailedSteps()
															 .get());
		} catch (Exception e) {
			log.error("Background task {} ({}) failed", progress.getId(), progress.getType(), e);
			progress.fail(e.getMessage() == null ? e.getClass()
													.getSimpleName() : e.getMessage());
		}
	}

	/**
	 * Identifier of the authenticated Vempain user, or null when the task is started without a user (schedules).
	 */
	public static Long currentUserId() {
		var authentication = SecurityContextHolder.getContext()
												  .getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl user) {
			return user.getId();
		}
		return null;
	}

	private static SecurityContext copyOfCurrentSecurityContext() {
		var copy = SecurityContextHolder.createEmptyContext();
		copy.setAuthentication(SecurityContextHolder.getContext()
													.getAuthentication());
		return copy;
	}

	private static ThreadPoolTaskExecutor newExecutor(int workerCount) {
		var executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(workerCount);
		executor.setMaxPoolSize(workerCount);
		executor.setQueueCapacity(1000);
		executor.setThreadNamePrefix("task-runner-");
		executor.initialize();
		return executor;
	}

	@PreDestroy
	void shutdown() {
		if (ownsExecutor && executor instanceof ThreadPoolTaskExecutor pool) {
			pool.shutdown();
		}
	}
}
