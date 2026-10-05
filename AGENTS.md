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
  in `PublishService.authorizeFileGroupPublish` before the asynchronous publish starts (the async thread has no security context);
  `publishAllFileGroups` skips groups the caller cannot fully modify.
- ACL behaviour is covered by `FileAclServiceITC`, `FileAclControllerCTC`, `FileAclPagedITC` and `FileAclRepairScheduleUTC`; keep positive and
  negative cases for every new ACL-dependent path and keep `FileAclService` at 95%+ line coverage.
- Prefer Jackson v3 `tools.jackson.databind.*` naming/mapper APIs for JSON configuration; keep non-`tools.jackson` annotations only when there is no
  `tools.jackson` replacement available in current dependencies.
- Test suffixes are meaningful and shared across the Vempain Java repos: `UTC` = unit test (Mockito), `CTC` = controller test (`AbstractControllerCTC`
    + MockMvc), `ITC` = integration test (Spring Boot + Testcontainers), `JTC` = JSON contract test (`RequestContractJTC`, `ResponseContractJTC`)
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
- Publishing resolves export files through `PublishService`, optionally resizes images, and uploads `FileIngestRequest` data to Admin through Feign clients.
  `VempainAdminTokenProvider` caches the Admin JWT and retries authentication failures.
- `UpdatedFileRefreshSchedule` persists its checkpoint in `scheduler_checkpoint`, verifies changed files with SHA-256, refreshes metadata and derivatives, and
  republishes only files whose `site_file_published` flag is true.
- GPS data publication is ephemeral. Music data is exposed at `/api/data-publish/music`; GPS series can be generated by directory path or by
  `file_group_id` plus `time_series_name`, with identifiers normalized to Admin-safe snake_case.
- Runtime API traffic uses `/api` on port 8080; actuator and Swagger are exposed on port 8081. Local runs require PostgreSQL, valid `vempain.*`
  settings, existing source/export roots, and `exiftool`.

## Tag ACL rule

Tags are metadata, not ACL-bearing resources. Tag entities have no ACL information, so tag list, search, and mutation endpoints must not perform ACL checks on
tags. ACL checks apply only to resources that explicitly carry an ACL.
