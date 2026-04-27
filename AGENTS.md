# vempain-file-backend — Agent Guide

## Architecture

Two-module Gradle project (Java 25, Spring Boot 4.0.5):

| Module     | Purpose                                                                             |
|------------|-------------------------------------------------------------------------------------|
| `api/`     | REST API interfaces, request/response DTOs — published as a JAR consumed by clients |
| `service/` | Spring Boot application: JPA entities, repositories, services, controllers          |

Authentication primitives (`AbstractVempainEntity`, `PagedRequest`, `PagedResponse`) come from the external library `fi.poltsi.vempain:vempain-auth-api`.

## Build & Run

```bash
./gradlew compileJava           # compile only
./gradlew build                 # compile + test
./gradlew clean test            # clean + integration tests (needs Docker DB)
./docker_db.sh                  # start local PostgreSQL container
```

Tests require `exiftool` installed on the host. GitHub Package Registry credentials must be set via `gpr.user`/`gpr.token` in `~/.gradle/gradle.properties` or
`GITHUB_ACTOR`/`GITHUB_TOKEN` env vars.

## File-Type Hierarchy

13 typed file categories, each a JPA entity that extends `FileEntity` (JOINED table inheritance):

`archive · audio · binary · data · document · executable · font · icon · image · interactive · thumb · vector · video`

- Base entity: `service/src/main/java/fi/poltsi/vempain/file/entity/FileEntity.java`
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

- JSON field names use snake_case (`@JsonNaming(SnakeCaseStrategy.class)` in DTOs)
- Test class suffix `ITC` = integration test, `UTC` = unit test
- After every code modification, run relevant tests for touched modules and report the results in the response
- Schema managed by Flyway; migrations under `service/src/main/resources/db/migration/`
- `FileGroupRepositoryImpl` uses raw native SQL — keep column names in sync with Flyway scripts
- Background refresh of modified source files runs via `UpdatedFileRefreshSchedulerService` (`vempain.refresh-updated-files.*`)
- Refresh checkpoints are persisted in `scheduler_checkpoint`; per-file admin publication knowledge is stored in `files.site_file_published`
- GPS dataset publication has two flows now:
    - `POST /api/data-publish/gps-timeseries/{directoryPath}` builds a canonical `gps_timeseries_<path>` identifier from the directory path.
    - `POST /api/data-publish/gps-timeseries` accepts `file_group_id` + `time_series_name`, normalizes the requested name to Admin-safe snake_case, and
      publishes that dataset for the selected file group.

## Further Reading

See [`docs/AGENTS.md`](docs/AGENTS.md) for deep-dive on GPS/location privacy guards, the publish pipeline, Feign client integrations, and scan/metadata
extraction flow.

