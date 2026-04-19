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
- Periodic modified-file refresh runs in `UpdatedFileRefreshSchedulerService`: it checks `files` rows against filesystem mtime since `scheduler_checkpoint`,
  verifies content changes by SHA-256, updates file metadata/type tables via `DirectoryProcessorService.refreshExistingOriginalFile`, refreshes linked
  `export_files`, and re-publishes site files only when `files.site_file_published` is known true.
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

## Music files and CSV dataset generation

### MusicFileEntity

Music files are a specialised subtype of `AudioFileEntity`. When `DirectoryProcessorService` processes an audio MIME-type file it probes the metadata via
`MetadataTool.hasMusicMetadata()` (checks for ID3/Vorbis artist, title, or album tags) and, if music metadata is present, stores the file as a
`MusicFileEntity` (type `MUSIC`) instead of a plain `AudioFileEntity` (type `AUDIO`).

Extra fields persisted in `music_files` (3-level JOINED inheritance: `files` → `audio_files` → `music_files`):
`artist`, `album`, `track_name`, `track_number`, `genre`.

Flyway migration: `V1002__music_files.sql`.

### DataService and DataPublishAPI

`DataService` generates CSV datasets on-demand and publishes them to Vempain Admin via `VempainAdminDataClient`
(a Feign client extending `fi.poltsi.vempain.admin.rest.DataAPI`).

Datasets are **ephemeral** — never stored in the file database. They are built at call time and sent straight to Admin.

Two dataset types are supported:

| Dataset | Endpoint | Identifier | CSV columns |
|---------|----------|------------|-------------|
| Music library | `POST /api/data-publish/music` | `music_library` | `artist, album, track_number, track_name, genre, duration_seconds` |
| GPS time-series | `POST /api/data-publish/gps-timeseries/{directoryPath}` | `gps_timeseries_<path>` | `timestamp, latitude, latitude_ref, longitude, longitude_ref, altitude, filename` |

#### Music dataset example

```
POST /api/data-publish/music
```

Reads all `MusicFileEntity` rows ordered by artist → album → track\_number, builds the CSV, and calls
`DataAPI.createDataSet` / `updateDataSet` + `publishDataSet` on Vempain Admin.

#### GPS time-series example

```
POST /api/data-publish/gps-timeseries/holidays_2024
```

Reads all `ImageFileEntity` rows whose `file_path` matches `/holidays/2024` and that have a non-null
`gps_location`, ordered by GPS timestamp (falls back to original-datetime). The resulting CSV is
published to Admin under identifier `gps_timeseries_holidays_2024`.

The `directoryPath` parameter in the URL is the path segment **after** the leading slash. Slashes must
not appear inside the segment (use underscores instead, or URL-encode). The service re-adds the leading
`/` before querying the database.

#### Create-or-update logic

`DataService.createOrUpdate()` first attempts `PUT` (update). If the Admin service responds with 404 it
falls back to `POST` (create). After a successful create/update it always calls the `publish` endpoint.


