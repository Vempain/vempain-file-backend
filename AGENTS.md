# vempain-file-backend — Agent Guide

## Architecture

Two-module Gradle project. Java and Spring Boot versions are pinned in `gradle/libs.versions.toml` (`java`, `spring-boot`) and must stay aligned with the other
Vempain backends:

|   Module   |                                       Purpose                                       |
|------------|-------------------------------------------------------------------------------------|
| `api/`     | REST API interfaces, request/response DTOs — published as a JAR consumed by clients |
| `service/` | Spring Boot application: JPA entities, repositories, services, controllers          |

Authentication primitives (`AbstractVempainEntity`, `PagedRequest`, `PagedResponse`) come from the external library `fi.poltsi.vempain:vempain-auth-api`.
Contract types shared with the admin backend (`FileTypeEnum`, `TagRequest`, `CopyrightRequest`, `LocationRequest`/`LocationResponse`, the task
DTOs and `TaskAPI`) come from `fi.poltsi.vempain:vempain-common-api` and the background task facility from `vempain-common-core`
(`fi.poltsi.vempain.common.task`). The admin API (`vempain-admin-backend-api`) is a dependency of the
`service` module only, for the Feign clients that call the admin backend; the `api` module must not depend on it, so that the admin API can
never end up depending on this artifact again (that cycle is what `vempain-common` removes). Add new shared types to `vempain-common`, release it,
then release the admin backend, then bump both versions in `gradle/libs.versions.toml` here.

## Build & Run

```bash
./gradlew compileJava           # compile only
./gradlew build                 # compile + test
./gradlew clean test            # clean + unit/integration tests (Testcontainers; Docker daemon required)
./docker_db.sh                  # start local PostgreSQL container for local app runs
```

## CLI contract synchronization

Any REST API change in this repository—endpoint, path, method, request/response DTO, JSON field, status code, or behavior—requires a corresponding
review and update of `vempain-cli`. Keep the CLI's commands and tests aligned with the published API contract before considering the backend change complete.

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
- User, unit and ACL management of this service's own user base is served by the controllers of `vempain-auth-core`
  (`fi.poltsi.vempain.auth.controller.{User,Unit,Acl}Controller`, paths `/content-management/users|units|acls`, administrator ACL
  required): `WebSecurityConfig` authenticates `/content-management/**` and `HostedManagementCTC` checks the hosting. The file frontend
  manages the file-side users here; it never talks to the admin backend for them. Units nest (auth migration `V2__unit_members.sql`),
  circular nesting is rejected with 400, and a principal carries every unit containing its direct units, which `FileAclService` relies on.
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
  in `PublishService.authorizeFileGroupPublish` before the background task starts; `publishAllFileGroups` (`POST /api/publish/all-file-groups`,
  optional body) skips groups the caller cannot fully modify.
- A single file is published with `POST /api/publish/file` (`PublishFileRequest`: `file_id` + optional `acls`): `PublishController`
  calls `PublishService.authorizeFilePublish` first (404 for an unknown file, 403 without the modify privilege), then
  `publishFile` submits a one-step `PUBLISH_FILE` task whose body `publishFileNow` uploads the export through the same `uploadFile`
  path as a group publish but with neither gallery ID nor gallery name, so the admin backend creates or updates the site file only and
  never a gallery. The created site file is registered as a compensation (removed again on cancel); a missing export fails the task.
- The admin and file user bases are separate: every publish logs in to the admin backend as the configured service account (`vempain.service.admin-backend-*`),
  so by default only that account is on the ACL of the created site files and gallery. A publish
  request can grant additional admin users and units through `acls` (`PublishAclRequest`: exactly one of `user_id`/`unit_id` of an admin
  account or unit + the four `*_privilege` flags, bean-validated: positive IDs, exactly one grantee, at least one privilege).
  `PublishService.toIngestAcls` copies them into every `FileIngestRequest` of the group (the admin backend validates them again before
  storing anything and answers `400` otherwise, which fails that file's step). The grantable admin users come from `GET /api/publish/users`
  (`PublishUserResponse`) and the grantable admin units from `GET /api/publish/units` (`PublishUnitResponse`), proxied by
  `PublishService.listPublishUsers`/`listPublishUnits` -> `VempainAdminService.listIngestUsers`/`listIngestUnits` (`FileIngestAPI`) with
  the same failure handling as the uploads; an unreachable admin backend answers `502`. Never resolve these IDs against this backend's
  own `user_account` or `unit` tables. The task runner propagates the caller's security context to the worker thread, but authorization that must fail the
  request itself always runs before `TaskRunner.submit`.
- A publish remembers what it created so that the next publish of the same file or group can show and resend the grantees of the previous
  one: every ingest answer (`FileIngestResponse`: `site_file_id`, `gallery_id`, `gallery_site_file_ids` = the site files of the gallery after
  the ingest, admin API 1.0.69+) is recorded by `PublishService.recordPublication` as `files.site_file_id` (`V1012`) plus
  `site_file_published=true` on the published file (single file, group and refresh republish alike); the file group keeps its `gallery_id`
  as before. The compensation of a created site file deletes it in the admin backend and puts the previous link back (`restoreSiteFileLink`); a replaced site
  file keeps its link. `GET /api/publish/file/{fileId}/status` (`PublishFileStatusResponse`:
  `published`, `site_file_id`, `acls`) and `GET /api/publish/file-group/{fileGroupId}/status` (`PublishFileGroupStatusResponse`: `published`,
  `gallery_id`, `gallery_name`, `site_file_ids`, `acls`) answer whether the admin backend still holds the site file or gallery and who was
  granted privileges on it (`PublishedAclResponse` rows, the shape of `PublishAclRequest`, never the service account's own row), asked
  through `VempainAdminService.getSiteFileAcls`/`getSiteFileAclsByFileId`/`getGalleryAcls` (`FileIngestAPI`; a `404` from the admin backend is
  "not published", `401`/`403` is `VempainAuthenticationException`, anything else `502`). A file without a recorded site file ID, or whose
  recorded site file is gone, is looked up by its own `files.id` (`site_file.file_id` is unique in the admin backend), so files published
  before `V1012` are found too; a group without `gallery_id` never asks the admin backend. Both need the modify privilege like the publish
  itself (404 unknown, 403 denied). `FileResponse` exposes `site_file_published` and `site_file_id`, `FileGroupResponse` the `gallery_id`.
  Tests: `PublishServiceUTC` (`PublicationStatus`, link recording and revert), `VempainAdminServiceUTC` (`AclLookups`), `PublishControllerCTC`,
  `ResponseContractJTC`.
- Long-running actions never block the HTTP request. They run through the shared durable task facility of `vempain-common-core`
  (`fi.poltsi.vempain.common.task`: `TaskRunner.submitDurable(type, title, totalSteps, payload, localFallback)` -> `TaskProgress`,
  `TaskProgressStore`, `TaskController` = `TaskAPI` at `/api/tasks`): the endpoint answers `202 TaskAcceptedResponse` and the frontend
  polls `GET /api/tasks/{task_id}` (`TaskProgressResponse`: status QUEUED/RUNNING/CANCELLING/CANCELLED/COMPLETED/FAILED, steps, percent,
  message, `cancel_requested`, `reverted_steps`, type specific `result`), cancels a running task with `POST /api/tasks/{task_id}/cancel` and
  dismisses a finished one with `DELETE`. Tasks are private to their owner, persisted in `task_record`/`task_compensation`
  (`V1009__durable_tasks.sql` plus the task lane columns of `V1013__task_lanes.sql`; the reference schema lives in `vempain-common-core`, which validates both
  at startup) and kept for `vempain.tasks.retention-minutes`.
  This service's part is `fi.poltsi.vempain.file.task.FileTaskCommandExecutor` (the `TaskCommandExecutor` bean that maps `TaskTypeEnum`
  + JSON payload to service calls and replays `TAG_REVERT_MUTATION` compensations) and `fi.poltsi.vempain.file.api.TaskTypeEnum`:
    publish single file / file group / all file groups, directory scan (result `ScanResponses`), music and GPS data set publishing (result admin
    `DataResponse`), the tag remove/replace/rename-across-all-files operations and the metadata value rewrite (`METADATA_UPDATE_VALUE`). Rules: validate and
    authorize synchronously before
    submitting; report one step per unit of work (`progress.advance`/`advanceFailed`); run transactional work through the Spring proxy
    (`applicationContext.getBean(...)`) because the task body runs outside the request transaction; a task submitted inside a transaction
    starts after that transaction commits. Cancellation is cooperative and reverting (saga style): call `progress.checkpoint()` before every
    unit of work and register the undo of every change with `progress.registerCompensation(description, () -> ...)` (or
    `registerDurableCompensation` for a replayable command) right after making it; on cancel or failure the runner runs the compensations
    most recent first. Current undo paths: publish removes the site files it created in the admin backend (`FileIngestAPI.deleteSiteFile`) and
    restores the file group's gallery link; a replaced site file cannot be restored. Scans remove the files, export files and file groups they
    created (`ScanRecorder`); refreshed files keep their refreshed state. Data set publishing deletes a created data set (`DataAPI.deleteDataSet`) or re-sends
    the previous content of a replaced one. Tag tasks apply the inverse metadata operation to every
    rewritten file and rename the tag back. Any new action that transfers files or data to another service, walks the filesystem or rewrites
    many files must use this facility; a new task type is added to `TaskTypeEnum` and `FileTaskCommandExecutor`.
    Wiring: `VempainFileServiceApplication` scans `fi.poltsi.vempain.common`, its repositories and entities; `WebSecurityConfig` permits
    `/tasks/**` for authenticated users; `vempain.tasks.*` in `application.yaml` configures workers, polling, leases and retention.
- ACL behaviour is covered by `FileAclServiceITC`, `FileAclControllerCTC`, `FileAclPagedITC` and `FileAclRepairScheduleUTC`; keep positive and
  negative cases for every new ACL-dependent path and keep `FileAclService` at 95%+ line coverage.
- Prefer Jackson v3 `tools.jackson.databind.*` naming/mapper APIs for JSON configuration; keep non-`tools.jackson` annotations only when there is no
  `tools.jackson` replacement available in current dependencies.
- Test suffixes are meaningful and shared across the Vempain Java repos: `UTC` = unit test (Mockito), `CTC` = controller test (`AbstractControllerCTC`
  + MockMvc), `ITC` = integration test (Spring Boot + Testcontainers), `JTC` = JSON contract test (`RequestContractJTC`, `ResponseContractJTC`).
    The task facility itself is tested in `vempain-common`; here `TaskControllerCTC` covers the hosted `/tasks` API and
    `FileTaskCommandExecutorUTC` the command dispatch. Services that submit tasks are unit tested with
    `new TaskRunner(new TaskProgressStore(), Runnable::run)` so the task body runs synchronously, or with a runner on a capturing
    executor (`queued::add`) when the test needs to cancel before the work runs; `TaskProgress.unmanaged(...)` builds a detached task.
    `AbstractControllerCTC` sets `vempain.tasks.poll-interval-ms`/`heartbeat-interval-ms` to one hour so the periodic worker poll cannot
    claim task rows a CTC creates and drives by hand; submitted tasks still run through the after-commit poll of `TaskRunner`.
- After every code modification, run relevant tests for touched modules and report the results in the response
- Schema managed by Flyway; migrations under `service/src/main/resources/db/migration/`
- `FileGroupRepositoryImpl` uses raw native SQL — keep column names in sync with Flyway scripts
- Background refresh of modified source files runs via `UpdatedFileRefreshSchedule` (`vempain.refresh-updated-files.*`)
- `PathCompletionService` (`POST /api/path-completion`, the directory pickers of the import view) always returns its completions in
  alphabetical order using a locale-aware `Collator` (`vempain.path-completion.collation-locale`, default `fi`: å, ä and ö sort after
  z and case only breaks ties); never return the raw filesystem order, it follows modification times and makes the lists jump.
  `PathCompletionServiceUTC`/`ITC` cover the non-ASCII ordering.
- Refresh checkpoints are persisted in `scheduler_checkpoint`; per-file admin publication knowledge is stored in `files.site_file_published` and
  `files.site_file_id` (set by every publish, see the publish section)
- Data dataset publication flows:
  - `POST /api/data-publish/music` generates/publishes `music_library` from `MusicFileEntity` rows.
  - `POST /api/data-publish/gps-timeseries` accepts `file_group_id` + `time_series_name`, normalizes the requested name to Admin-safe snake_case, and
    publishes that dataset for the selected file group.

## Operational details

- Scanning processes leaf directories below the configured original/export roots. `DirectoryProcessorService` owns file, subtype, metadata, tag, GPS,
  and export-file persistence. Export derivatives are linked through `originalDocumentId` and are skipped when their original entity is absent.
- Calls to the admin backend authenticate with a service-to-service API token: `VempainAdminFeignConfig` sends
  `vempain.service.admin-backend-api-token` in the `X-Vempain-Api-Token` header (`Constants.API_TOKEN_HEADER` of the admin API) on every
  Feign call. The token is created in the admin UI (Administration > API tokens, bound to this service's network and an expiry) and
  configured through `ENV_VEMPAIN_ADMIN_BACKEND_API_TOKEN`; there is no login, JWT or password any more (`SetupVerification` requires the
  property). A 401/403 from the admin backend means the token is wrong, expired, revoked or used from another network and surfaces as
  `VempainAuthenticationException` without any retry.
- In the `prod` profile `SetupVerification.verifyProductionSettings` refuses to start unless the admin backend URL is HTTPS and
  `spring.datasource.url` requires TLS (`sslmode=require`, `verify-ca` or `verify-full`, or `ssl` without an `sslmode`); `prefer`/`allow` do
  not count because they fall back to plaintext. The `vempain-cluster` databases also reject plaintext connections.
- Every `FileIngestRequest` built by `PublishService.uploadFile` carries `file_id` = the `files.id` of the published entity (admin API releases after 1.0.64
  require it and store it as `site_file.file_id`); keep it set in every publish path (single file, group, republish).
- **IMPERATIVE:** When a file is published in `PublishService`, the `metadata` field of the `FileIngestRequest` DTO MUST contain only the metadata defined by
  `FileMetadataTool.collectStandardMetadataAsJson(...)` (the `MetadataJson.collectStandardMetadataAsJson` of `vempain-common`), built from the file's
  `metadata` rows and `FileEntity`. Never send the raw metadata rows, the full `exiftool -json` output, or any other metadata set in
  `FileIngestRequest.metadata`. The same standard JSON is the only metadata passed to `ImageTool.resizeImage`. Any change to the published metadata
  must be made in `MetadataJson.collectStandardMetadataAsJson` of `vempain-common`, not in the publish path.
- Publishing resolves export files through `PublishService`, optionally resizes images, and uploads `FileIngestRequest` data to Admin through Feign clients,
  as a background task with one step per exported file, also for publish-all (groups processed sequentially, a group without exported files counts
  as one step and a group that fails as a whole marks its remaining file steps as failed). Only the converted (exported) files are transferred, so a
  file without an `export_files` row is skipped silently and is no step; an export that is registered but missing on disk is a failed step. The
  totals are computed when the task is submitted (`FileGroupRepository.countExportedFilesById`, the `exported_file_count` of the group search, never
  the original `file_count`), so `total_steps` is right from the first poll; the progress rows commit in their own transaction (`vempain-common` 1.0.4+) and are
  therefore visible while the group's transaction is open.
  `VempainAdminTokenProvider` caches the Admin JWT and retries authentication failures.
- `UpdatedFileRefreshSchedule` persists its checkpoint in `scheduler_checkpoint`, verifies changed files with SHA-256, refreshes metadata and derivatives, and
  republishes only files whose `site_file_published` flag is true.
- GPS data publication is ephemeral. Music data is exposed at `/api/data-publish/music`; GPS series can be generated by directory path or by
  `file_group_id` plus `time_series_name`, with identifiers normalized to Admin-safe snake_case.
- Runtime API traffic uses `/api` on port 8080; actuator and Swagger are exposed on port 8081. Local runs require PostgreSQL, valid `vempain.*`
  settings, existing source/export roots, and `exiftool`.
- Metrics are pushed, never scraped: `/actuator/prometheus` must stay out of `management.endpoints.web.exposure.include` because the management
  port is routed publicly through Traefik. The backend exports OTLP/HTTP (`micrometer-registry-otlp`) to the monitoring Prometheus when
  `VEMPAIN_METRICS_ENABLED=true`; `VEMPAIN_METRICS_URL`, `VEMPAIN_METRICS_USERNAME`/`VEMPAIN_METRICS_PASSWORD` (basic auth),
  `VEMPAIN_METRICS_CERTIFICATE` (PEM of the self-signed endpoint, trusted through an SSL bundle), `VEMPAIN_METRICS_STEP` and
  `VEMPAIN_METRICS_ROLE` (`api` or `scheduler`, a common tag next to `application` and `instance`) feed the `vempain.metrics.*` properties.
  The translation of `vempain.metrics.*` into the Boot `management.otlp.metrics.export.*` properties (URL, `Authorization` header, SSL bundle)
  is the `MetricsExportEnvironmentPostProcessor` of `vempain-common` core; this backend only carries the properties, so bump `vempain-common`
  in `gradle/libs.versions.toml` to the release that ships it (together with the shared `vempain.tasks.*` task meters).
- Tracing: `spring-boot-starter-opentelemetry` makes the backend continue the W3C `traceparent` it receives (from Traefik or the SPA, B3 is
  consumed too) and produce W3C on the way out; Boot's default log pattern carries `[app,traceId,spanId]`, so there is no custom logback
  configuration here. The Feign calls to the admin backend carry the context by themselves: OpenFeign registers the `feign-micrometer`
  observation capability in every client context, which opens a client span (a child of the request or task span) and the propagating
  observation handler injects its `traceparent` (`VempainAdminTracePropagationITC` pins the header against a stub admin backend); do not
  add a hand-written interceptor. Background tasks get a span `task <type>` and `taskId` in the MDC from `vempain-common`'s `TaskRunner`.
  Both `management.tracing.enabled` and `management.tracing.export.enabled` are bound to `vempain.tracing.enabled` (`VEMPAIN_TRACING_ENABLED`,
  default false): `export.enabled` is the switch Boot's tracing conditions evaluate, it turns on the propagators, the OTLP exporter and the
  `[app,traceId,spanId]` block of the console pattern, so the three backends behave identically. `VEMPAIN_TRACING_URL` and
  `VEMPAIN_TRACING_SAMPLING` feed `vempain.tracing.*`; the translation into `management.opentelemetry.tracing.export.otlp.*` (endpoint,
  `Authorization` from the metrics credentials, the `vempain-metrics` SSL bundle) is the same `MetricsExportEnvironmentPostProcessor` of
  `vempain-common`, so the bump above covers it. Tests that need tracing set `management.tracing.export.enabled=true` without an OTLP
  endpoint, which enables propagation and sends nothing.
- Custom meters live in `fi.poltsi.vempain.file.metrics`: `FileMetrics` owns every name (`vempain.scan.files.scanned|new{directory}`,
  `vempain.scan.duration{directory,result}`, `vempain.file_processing.queue{status,file_type}` gauge, `vempain.file_processing.items{result,file_type}`,
  `vempain.file_processing.attempts{file_type}`, `vempain.thumbnails.generated{result}`, `vempain.thumbnails.missing`,
  `vempain.publish.files{result=uploaded|updated|skipped_no_export|missing_on_disk|failed}`, `vempain.publish.bytes`,
  `vempain.admin_backend.rejected{status}`, `vempain.acl.repaired`, `vempain.refresh.files.verified|republished`,
  `vempain.refresh.checkpoint.age.seconds`, `vempain.inventory.files{file_type}`, `vempain.inventory.export_files`,
  `vempain.inventory.file_groups[.published]`); `FileMetricsRefresher` feeds the database backed gauges once a minute (`vempain.metrics.gauge-refresh-ms`);
  `ToolMetrics` times the static FFmpeg calls as `vempain.tool.duration{tool=ffmpeg,result}` on the global registry and the shared
  `ExifToolService` of `vempain-common` records the exiftool runs under the same meter. Feign calls to the admin backend report `http.client.requests` through
  `feign-micrometer`; the Hikari pool is named `file`
  and Tomcat's MBean registry is enabled for the `tomcat.*` meters. Tags are bounded enumerations only, never ids or file names. Services take
  `FileMetrics` as a constructor dependency; unit tests pass a `FileMetrics(new SimpleMeterRegistry())` or a mock.
- The OTLP push reports itself: `MeteredOtlpMetricsSender.Registrar` (a `BeanPostProcessor`) wraps the `OtlpMetricsSender` Boot builds and
  reports every push to `MetricsExportMetrics` (`vempain.metrics_export.requests{result=success|rejected|error,status}`,
  `vempain.metrics_export.duration{result}`, `vempain.metrics_export.bytes`, the gauges `vempain.metrics_export.last_success.age.seconds`
  (-1 before the first success) and `vempain.metrics_export.meters`). The same two classes exist in the admin backend; the monitoring dashboard
  `vempain-application.json` has a "Metrics export" row for them. Keep Boot's OTLP auto-configuration untouched: the sender is decorated, never replaced.
- A file's `description` column comes from `MetadataJson.extractDescription` (scan and refresh): `XMP-dc:Description`, then
  `IFD0:ImageDescription`, then the legacy `IPTC:Caption-Abstract`, using the family 1 group names of the scanner's `exiftool -g1 -J`
  output (there is no `XMP` group in it). Thumbnails (`ThumbnailGenerationService`) carry the description of the file they were made
  from, never their pixel size; `V1011` backfilled both from the `metadata` table and the thumbnails' target files.
- Metadata management (`MetadataAPI` at `/metadata`, `MetadataController` -> `MetadataService`) browses the `metadata` table (one row per
  file, ExifTool family 1 group and key, filled by `DirectoryProcessorService.processMetadata` from `exiftool -g1 -J`) and rewrites values:
  `POST /metadata/paged` (`MetadataSearchRequest`: contains-filters on `metadata_group`/`metadata_key`/`metadata_value`, sort by
  group/key/value/`file_count`) returns distinct group/key/value rows with system-wide file counts (`MetadataSummaryResponse`, native SQL
  in `MetadataRepositoryImpl`, index `V1010`); `POST /metadata/files/paged` (`MetadataFilesRequest`: the triple plus `filename`/`file_type`
  filters, sort through `FileSearchHelper.buildSort`) lists only files passing `FileAclService.readableFiles`; `POST /metadata/value`
  (`MetadataValueUpdateRequest`: the triple plus `new_value`, empty removes the entry) answers `202` with a `METADATA_UPDATE_VALUE` task
  after `requireModifyBulk` on every file carrying the value. The task body `MetadataService.applyValueUpdate` runs
  `ExifToolService.writeValue` (`exiftool -GROUP:KEY=value`, in place) on the original and its export file, updates the export
  checksum and thumbnail, re-reads the file through `DirectoryProcessorService.refreshExistingOriginalFile` (which rebuilds the file's
  `metadata` rows) and registers a `METADATA_REVERT_VALUE` compensation per file (replayed by `FileTaskCommandExecutor`). Group and key
  names must match `ExifToolService.isValidTagName` (`[A-Za-z0-9][A-Za-z0-9_-]*`, so request text can never become an ExifTool option or
  `+=`/`<=` operator) and the groups `ExifTool`, `File`, `System` and `Composite` are read-only (`isWritableGroup`, reported as
  `writable=false` in the summary). Metadata rows themselves carry no ACL. Covered by `MetadataServiceUTC`, `MetadataControllerCTC` (end
  to end with ExifTool) and `FileMetadataToolITC`.
- ExifTool runs only through the shared `fi.poltsi.vempain.common.metadata.ExifToolService` of `vempain-common` (persistent `-stay_open`
  processes, `vempain.exiftool.path|stay-open|pool-size|idle-timeout-ms` in `application.yaml`, `VEMPAIN_EXIFTOOL_*` in the container);
  `DirectoryProcessorService`, `TagService`, `MetadataService`, `PublishService`, `DerivativeLookupService`, `UpdatedFileRefreshSchedule`
  and `ImageTool` take it as a constructor dependency, and the JSON is parsed with the static `MetadataJson`. One full read per file serves
  every per-type field of the scan and the refresh (`MetadataJson.extractFrameRate(json)`, `extractAudioBitRate(json, size)`,
  `extractDocumentPageCount(json)`, ...); never run ExifTool again for a single tag of a file that was just read. The publish and the
  refresh read the MIME type of a new export with one `readTag(file, "MIMEType")` plus `Files.probeContentType`. `tools/FileMetadataTool`
  keeps what is specific to this backend: the FFmpeg codec probe (`extractVideoCodec`), `extractGpsData` as a `GpsLocationEntity` and
  `collectStandardMetadataAsJson` over `MetadataEntity` rows. Tests: `FileMetadataToolUTC`, `FileMetadataToolITC` (every file type from one
  grouped read, real exiftool and FFmpeg), `ImageToolUTC` and the service UTCs mock `ExifToolService`; `tools/TestExifTool` offers one real
  service for tests that prepare files with ExifTool. Never add a `ProcessBuilder("exiftool")` to this backend again.
- Every `LIKE` pattern built from request text goes through `fi.poltsi.vempain.file.tools.LikePatterns` (`contains`/`containsIgnoreCase`/`prefix`: escapes
  `%`, `_` and `\\`, truncates to 200 characters; `limitTokens` caps a search at 10 tokens) and declares the escape character,
  as in `FileGroupRepositoryImpl` and `MetadataRepositoryImpl` (native SQL, `ESCAPE_CLAUSE`), `FileSearchHelper`, `MetadataService.findFiles` and
  `TagService` tag search (Criteria API, `ESCAPE_CHAR`). Request text
  never acts as wildcard syntax (OWASP A05).
- Security findings and their mitigations are recorded in `security/OWASP-2025-audit-report.md`; keep it current when changing authorization,
  query building, filesystem handling or input validation.

## Container image

- `Dockerfile` is runtime-only (the CI-built jar is added as `/app.jar`) and starts `container-entrypoint.sh` as root: it builds the private
  JVM truststore from the mounted admin certificate, gives the image's `vempain` account the ids `VEMPAIN_UID`/`VEMPAIN_GID` (default
  `6666`, set per container in the `vempain-cluster` stacks as `ENV_VEMPAIN_UID`/`ENV_VEMPAIN_GID`) by rewriting `/etc/passwd` and
  `/etc/group`, and runs Java through `su-exec` as those ids; a container started as a non-root user keeps that user. The ids must be the
  host owner of the mounted `/vempain_file/original` and `/vempain_file/export`. The same script, apart from the truststore, is in the website backend; keep the
  user handling of
  the three identical.

## Tag ACL rule

Tags are metadata, not ACL-bearing resources. Tag entities have no ACL information, so tag list, search, and mutation endpoints must not perform ACL checks on
tags. ACL checks apply only to resources that explicitly carry an ACL.
