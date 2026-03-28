# AGENTS.md

## Repo map

- This is a 2-module Gradle build: `api/` contains the public REST interfaces + DTOs, and `service/` is the Spring Boot application (`settings.gradle`,
  `api/build.gradle`, `service/build.gradle`).
- Keep HTTP contracts in `api/src/main/java/fi/poltsi/vempain/file/rest/**`; service controllers implement those interfaces in
  `service/src/main/java/fi/poltsi/vempain/file/controller/**` (for example `PublishController` -> `PublishAPI`, `FileScannerController` -> `FileScannerAPI`).
- The app intentionally scans `fi.poltsi.vempain.auth` repositories/entities too (`VempainFileServiceApplication`), so auth behavior comes from the private
  `vempain-auth-*` packages, not this repo alone.

## Big-picture flow

- File ingestion starts at `/scan-files` and runs through `FileScannerService` -> `DirectoryProcessorService`.
- Scanning only processes **leaf directories** under `vempain.original-root-directory` / `vempain.export-root-directory`; each leaf directory becomes a
  `file_group` row (`README.md`, `FileScannerService`, `DirectoryProcessorService`).
- `DirectoryProcessorService` is the core persistence pipeline: it creates/updates `files`, subtype tables (`image_files`, `video_files`, etc.), `metadata`,
  `tags`, `gps_locations`, and `export_files` (`service/src/main/resources/db/migration/V1000__init.sql`).
- Exported/derivative files are linked back to originals by `originalDocumentId`; derivatives are skipped as orphaned if the original file entity does not
  already exist (`DirectoryProcessorService.processExportDirectory`).
- Publishing runs through `PublishService`: it resolves the derivative file from `export_files`, optionally resizes images, builds `FileIngestRequest`, and
  uploads to Vempain Admin.
- `publishAllFileGroups()` pages through groups with the native-query search in `FileGroupRepositoryImpl`, then calls `PublishService` through the Spring proxy
  so `@Async` works. Progress is only in-memory via `PublishProgressStore`.
- GPS privacy is business logic here: `PublishService` only includes `fileEntity.getGpsLocation()` when `LocationService.isGuardedLocation(...)` says it is
  outside all guards.

## Local setup and workflows

- Use Java **25** for local work: CI, Gradle toolchain, and Docker all target 25 (`gradle.properties`, `.github/workflows/ci.yaml`, `Dockerfile`). `README.md`
  still mentions Java 21.
- Private dependencies come from GitHub Packages. Builds need `gpr.user` / `gpr.token` Gradle properties or `GITHUB_ACTOR` / `GITHUB_TOKEN` env vars (
  `api/build.gradle`, `service/build.gradle`).
- `exiftool` is a hard dependency for metadata extraction and some tests; CI installs it explicitly and the Docker image adds it (`MetadataTool`,
  `.github/workflows/ci.yaml`, `Dockerfile`).
- Fastest verification is `./gradlew clean test`.
- For a local app run, start Postgres with `./docker_db.sh`, then run the app with the required overrides from `start.sh` / `README.md`.
- The app will shut itself down on startup if required `vempain.*` settings are left as `override-me` or if the original/export root paths do not exist (
  `SetupVerification`).
- Runtime ports are split: API on `http://localhost:8080/api`, actuator + Swagger/OpenAPI on `http://localhost:8081/actuator/swagger-ui/index.html` (
  `application.yaml`).

## Codebase conventions that matter

- JSON DTOs use **snake_case** through `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` in `api/**`; request/response examples should follow
  that. Example: `ScanRequest` expects `original_directory` / `export_directory`.
- Be careful with repo scripts: `scripts/testScanning.sh` still posts `directory_name`, which does not match the current `ScanRequest` fields.
- Entity-to-API mapping usually lives on the entity itself via `toResponse()` methods (`FileEntity`, `FileGroupEntity`, `GpsLocationEntity`,
  `LocationGuardEntity`, `ExportFileEntity`). Reuse those instead of adding parallel mappers.
- Relative file paths are stored with a leading `/` (see `computeRelativeFilePath()`); publishing strips that slash before sending to Admin (
  `PublishService.normalizeIngestPath`).
- Java formatting uses tabs, max line length 160, and IntelliJ-oriented `.editorconfig` rules; preserve existing alignment-heavy style.
- Test naming is meaningful here: `*ITC` covers integration/service-level tests, while `*UTC` is used for focused utility/unit tests (`PublishServiceITC`,
  `LocationServiceITC`, `MetadataToolUTC`).
- Integration tests use Testcontainers Postgres from `service/src/test/resources/application.yaml`; some tests also declare explicit `PostgreSQLContainer`
  instances.

## External integrations

- Vempain Admin upload/login happens through OpenFeign clients in `service/src/main/java/fi/poltsi/vempain/file/feign/**`.
- `VempainAdminTokenProvider` caches the JWT for ~1 hour and forces re-login on expiry; `VempainAdminService` retries upload after auth failures.
- The database schema is Flyway-managed; if you change entities or repository SQL, update `service/src/main/resources/db/migration/**` and the native SQL in
  `FileGroupRepositoryImpl` together.

