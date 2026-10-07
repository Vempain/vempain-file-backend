# vempain-file-backend — Agent Guide

## Architecture

Two-module Gradle project. Java and Spring Boot versions are pinned in `gradle/libs.versions.toml` (`java`, `spring-boot`) and must stay aligned with the other
Vempain backends:

| Module     | Purpose                                                                             |
|------------|-------------------------------------------------------------------------------------|
| `api/`     | REST API interfaces, request/response DTOs — published as a JAR consumed by clients |
| `service/` | Spring Boot application: JPA entities, repositories, services, controllers          |

Authentication primitives (`AbstractVempainEntity`, `PagedRequest`, `PagedResponse`) come from the external library `fi.poltsi.vempain:vempain-auth-api`.

## Build & Run

```bash
./gradlew compileJava           # compile only
./gradlew build                 # compile + test
./gradlew clean test            # clean + unit/integration tests (Testcontainers; Docker daemon required)
./docker_db.sh                  # start local PostgreSQL container for local app runs
```

Tests require `exiftool` installed on the host. GitHub Package Registry credentials must be set via `gpr.user`/`gpr.token` in `~/.gradle/gradle.properties` or
`GITHUB_ACTOR`/`GITHUB_TOKEN` env vars.

## File-Type Hierarchy

14 typed file categories, each a JPA entity that extends `FileEntity` (JOINED table inheritance):

`archive · audio · binary · data · document · executable · font · icon · image · interactive · music · thumb · vector · video`

- Base entity: `service/src/main/java/fi/poltsi/vempain/file/entity/FileEntity.java`
- Music subtype: `service/src/main/java/fi/poltsi/vempain/file/entity/MusicFileEntity.java` (extends `AudioFileEntity`)
- Common searchable fields: `filename`, `filePath`, `description`, `mimetype`
- Entity → DTO: call `entity.toResponse()` (implemented in every typed entity)

## Paged List Pattern (canonical)

All `findAll` endpoints use **POST** to `<BASE_PATH>/paged` with a `@RequestBody PagedRequest`.

Reference implementation: `FileGroupAPI.java` / `FileGroupController.java` / `FileGroupService.java`.

For typed file endpoints:

1. **API interface** (`api/.../rest/files/XxxFileAPI.java`) — `@PostMapping(BASE_PATH + "/paged")` accepting `@Valid @RequestBody PagedRequest`
2. **Repository** (`service/.../repository/files/XxxFileRepository.java`) — extends `JpaRepository<Entity, Long>, JpaSpecificationExecutor<Entity>`
3. **Service** (`service/.../service/files/XxxFileService.java`) — use `FileSearchHelper.buildSpecification(search, caseSensitive)` and
   `FileSearchHelper.buildSort(sortBy, direction)` from `FileSearchHelper.java`
4. **Controller** — delegates straight to the service with the `PagedRequest`

For complex native-SQL search (e.g. across joined tables), follow `FileGroupRepositoryImpl.java`.

## Adding a New File Type

1. Create entity extending `FileEntity` in `service/.../entity/`
2. Create repository extending `JpaRepository<E, Long>, JpaSpecificationExecutor<E>`
3. Create service using `FileSearchHelper`; expose `findAll(PagedRequest)`, `findById(long)`, `delete(long)`
4. Create API interface in `api/.../rest/files/` with `POST /paged`, `GET /{id}`, `DELETE /{id}`
5. Create controller in `service/.../controller/files/` implementing the API interface

## Key Conventions

- JSON field names use snake_case (`@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` from `tools.jackson.databind` on DTOs)
- Snake_case is mandatory for all API JSON contracts; never add camelCase JSON field names in DTO annotations, request/response payloads, or docs/examples.
- Prefer Lombok annotations for applicable Java boilerplate such as constructors, accessors, builders, and logging, unless they obscure behavior or conflict
  with framework requirements.
- Vempain authorization is resource-based ACL authorization, not role-based access control. `FileEntity` (all 14 typed files) is the only
  ACL-linked resource here: it extends `AbstractVempainEntity` and carries an `acl_id`. Tags, file groups, locations, location guards,
  metadata, export files, queues and checkpoints have no ACL and must never be ACL-checked; checks on tag operations apply to the *files*
  being tagged, not to the tag.
- All file authorization goes through `FileAclService` (built on `AclAuthorizationService` from `vempain-auth`): `readableFiles()` is the
  JPA Specification every paged listing must include, `requireRead/Modify/Delete` guard single-file endpoints and content download, and
  `requireModify(files)`/`canModifyAll(files)` guard file-group publishing. Keep only endpoint authentication in `WebSecurityConfig`; do not
  use `hasRole`, `ROLE_*` or administrator-only route matchers.
- Authorization fails closed: a file whose `acl_id` is not positive or whose ACL rows are missing is denied for everyone. Never add a
  "no ACL means public" shortcut or a test-mode bypass. `FileAclRepairSchedule` (`vempain.acl-repair.*`, daily) creates a new ACL with all
  privileges for the file's `creator` and links it to such files.
- Deliberately **not** ACL-filtered, because they are system-wide operations rather than per-resource reads: directory scanning (`DirectoryProcessorService`),
  data-set generation for the site (`DataService` music and GPS time series, `MusicFileService.findAllOrdered`),
  `StatisticsService` counts, the refresh/thumbnail/video schedules, and the admin-side republish of already published files. Do not add
  per-file ACL checks to these paths; they do require an authenticated caller like every other endpoint.
- Publishing a file group (`POST /api/publish/file-group`) requires the modify privilege on every file of the group, checked synchronously
  in `PublishService.authorizeFileGroupPublish` before the background task starts; `publishAllFileGroups` skips groups the caller cannot
  fully modify. The task runner propagates the caller's security context to the worker thread, but authorization that must fail the
  request itself always runs before `TaskRunner.submit`.
- Long-running actions never block the HTTP request. They run through the task progress facility in `fi.poltsi.vempain.file.task`
  (`TaskRunner.submit(type, title, totalSteps, work)` -> `TaskProgress`, `TaskProgressStore`, `TaskController` = `TaskAPI` at `/api/tasks`):
  the endpoint answers `202 TaskAcceptedResponse` and the frontend polls `GET /api/tasks/{task_id}` (`TaskProgressResponse`: status
  QUEUED/RUNNING/CANCELLING/CANCELLED/COMPLETED/FAILED, steps, percent, message, `cancel_requested`, `reverted_steps`, type specific `result`),
  cancels a running task with `POST /api/tasks/{task_id}/cancel` and dismisses a finished one with `DELETE`. Tasks are private to their owner
  and kept for `vempain.tasks.retention-minutes`. Current task types (`TaskTypeEnum`): publish file group / all file
  groups, directory scan (result `ScanResponses`), music and GPS data set publishing (result admin `DataResponse`), and the tag
  remove/replace/rename-across-all-files operations. Rules: validate and authorize synchronously before submitting; report one step per
  unit of work (`progress.advance`/`advanceFailed`); run transactional work through the Spring proxy (`applicationContext.getBean(...)`)
  because the task body runs outside the request transaction; a task submitted inside a transaction starts after that transaction commits.
  Cancellation is cooperative and reverting (saga style): call `progress.checkpoint()` before every unit of work and register the undo of
  every change with `progress.registerCompensation(description, () -> ...)` right after making it; on cancel or failure the runner runs
  the compensations most recent first. Current undo paths: publish removes the site files it created in the admin backend (`FileIngestAPI.deleteSiteFile`) and
  restores the file group's gallery link; a replaced site file cannot be restored. Scans remove the
  files, export files and file groups they created (`ScanRecorder`); refreshed files keep their refreshed state. Data set publishing deletes
  a created data set (`DataAPI.deleteDataSet`) or re-sends the previous content of a replaced one. Tag tasks apply the inverse metadata
  operation to every rewritten file and rename the tag back.
  Any new action that transfers files or data to another service, walks the filesystem or rewrites many files must use this facility.
  The facility is intentionally free of file backend types so that it can be extracted into a shared `vempain-auth`/frontend component later.
- ACL behaviour is covered by `FileAclServiceITC`, `FileAclControllerCTC`, `FileAclPagedITC` and `FileAclRepairScheduleUTC`; keep positive and
  negative cases for every new ACL-dependent path and keep `FileAclService` at 95%+ line coverage.
- Prefer Jackson v3 `tools.jackson.databind.*` naming/mapper APIs for JSON configuration; keep non-`tools.jackson` annotations only when there is no
  `tools.jackson` replacement available in current dependencies.
- Test suffixes are meaningful and shared across the Vempain Java repos: `UTC` = unit test (Mockito), `CTC` = controller test (`AbstractControllerCTC`
    + MockMvc), `ITC` = integration test (Spring Boot + Testcontainers), `JTC` = JSON contract test (`RequestContractJTC`, `ResponseContractJTC`).
      The task facility is covered by `TaskProgressStoreUTC`, `TaskRunnerUTC` and `TaskControllerCTC`; services that submit tasks are unit
      tested with `new TaskRunner(new TaskProgressStore(), Runnable::run)` so the task body runs synchronously, or with a runner on a
      capturing executor (`queued::add`) when the test needs to cancel before the work runs.
- After every code modification, run relevant tests for touched modules and report the results in the response
- Schema managed by Flyway; migrations under `service/src/main/resources/db/migration/`
- `FileGroupRepositoryImpl` uses raw native SQL — keep column names in sync with Flyway scripts
- Background refresh of modified source files runs via `UpdatedFileRefreshSchedule` (`vempain.refresh-updated-files.*`)
- Refresh checkpoints are persisted in `scheduler_checkpoint`; per-file admin publication knowledge is stored in `files.site_file_published`
- Data dataset publication flows:
    - `POST /api/data-publish/music` generates/publishes `music_library` from `MusicFileEntity` rows.
    - `POST /api/data-publish/gps-timeseries` accepts `file_group_id` + `time_series_name`, normalizes the requested name to Admin-safe snake_case, and
      publishes that dataset for the selected file group.

## Operational details

- Scanning processes leaf directories below the configured original/export roots. `DirectoryProcessorService` owns file, subtype, metadata, tag, GPS,
  and export-file persistence. Export derivatives are linked through `originalDocumentId` and are skipped when their original entity is absent.
- Publishing resolves export files through `PublishService`, optionally resizes images, and uploads `FileIngestRequest` data to Admin through Feign clients,
  as a background task with one step per file (publish-all: one step per group, groups processed sequentially).
  `VempainAdminTokenProvider` caches the Admin JWT and retries authentication failures.
- `UpdatedFileRefreshSchedule` persists its checkpoint in `scheduler_checkpoint`, verifies changed files with SHA-256, refreshes metadata and derivatives, and
  republishes only files whose `site_file_published` flag is true.
- GPS data publication is ephemeral. Music data is exposed at `/api/data-publish/music`; GPS series can be generated by directory path or by
  `file_group_id` plus `time_series_name`, with identifiers normalized to Admin-safe snake_case.
- Runtime API traffic uses `/api` on port 8080; actuator and Swagger are exposed on port 8081. Local runs require PostgreSQL, valid `vempain.*`
  settings, existing source/export roots, and `exiftool`.

- Every `LIKE` pattern built from request text goes through `fi.poltsi.vempain.file.tools.LikePatterns` (`contains`/`containsIgnoreCase`/`prefix`: escapes
  `%`, `_` and `\\`, truncates to 200 characters; `limitTokens` caps a search at 10 tokens) and declares the escape character,
  as in `FileGroupRepositoryImpl` (native SQL, `ESCAPE_CLAUSE`), `FileSearchHelper` and `TagService` tag search (Criteria API, `ESCAPE_CHAR`). Request text
  never acts as wildcard syntax (OWASP A05).

## Tag ACL rule

Tags are metadata, not ACL-bearing resources. Tag entities have no ACL information, so tag list, search, and mutation endpoints must not perform ACL checks on
tags. ACL checks apply only to resources that explicitly carry an ACL.
