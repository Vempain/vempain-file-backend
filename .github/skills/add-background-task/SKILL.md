---
name: add-background-task
description: Run a long operation of the file backend (file transfers, filesystem walks, bulk metadata rewrites) as a durable background task with progress, cancel and compensations. Use when an endpoint would block the request or must report progress to the task tray.
---

# Add a background task to vempain-file-backend

The task facility comes from `vempain-common-core` (`fi.poltsi.vempain.common.task`): `TaskRunner`, `TaskProgress`, `TaskProgressStore`,
`/api/tasks`. This service maps task types to work in `fi.poltsi.vempain.file.task.FileTaskCommandExecutor`.

## Steps

1. Add the type to `api/.../api/TaskTypeEnum.java` (javadoc the result payload) and to the `allowableValues` of its `@Schema`.
2. In the service write the pair:
    - `xxxAsTask(request)`: validate, authorize (`fileAclService.requireModifyBulk(files)` for bulk work), compute the real total (one step per smallest item,
      e.g. per file; `Math.max(1, count)` for an empty set) and call
      `taskRunner.submitDurable(type.name(), title, totalSteps, payload, progress -> proxy.xxxNow(..., progress))`.
      The payload must be JSON serialisable (request DTO + id lists in a `Map`).
    - `xxxNow(..., TaskProgress progress)`: the body, `@Transactional`, obtained through the Spring proxy (`applicationContext.getBean(XxxService.class)`)
      because it runs on a worker thread. Per item: `progress.checkpoint()` before the
      work, then `progress.advance(message)` or `advanceFailed(message)`. Register the undo of every change right after making it:
      `registerDurableCompensation(description, "COMMAND", payloadMap)` when `progress.isDurable()`, otherwise
      `registerCompensation(description, () -> proxy.revertXxx(...))` for the in-memory runner of unit tests.
3. Add the `case` to `FileTaskCommandExecutor.execute` (deserialize the payload with `objectMapper`, call `xxxForTask`, which resolves
   the proxy) and the compensation command to `compensate`.
4. Controller answers `ResponseEntity.accepted().body(progress.toAcceptedResponse())` (`202 TaskAcceptedResponse`).
5. Frontend: add the type to `vempain-file-frontend/src/models/TaskTypeEnum.ts`; the screen calls `trackTask(accepted, {onFinished})`.

## Rules

- Totals are known at submit time; `setTotalSteps` inside the body only corrects them.
- Progress writes commit in their own transaction (`vempain-common` 1.0.4+), so reporting inside a transactional body is fine.
- Cancellation is cooperative: never catch `TaskCancelledException`; rethrow it in catch-all blocks.
- A failing item marks only its own step (s) failed; the task completes. A thrown exception fails the whole task and runs the compensations.

## Tests

- Service `*UTC`: `new TaskRunner(new TaskProgressStore(), Runnable::run)` runs the body synchronously; `queued::add` executor to assert
  the submit without running; `TaskProgress.unmanaged(...)` for the body alone. Assert type, title, total steps, status and messages.
- `FileTaskCommandExecutorUTC`: dispatch of the new type and compensation.
- A `*CTC` driving the endpoint end to end (`awaitTaskCompletion` pattern in `TagControllerCTC`/`MetadataControllerCTC`).
- Document the task in `AGENTS.md` (task list in the background task paragraph).
