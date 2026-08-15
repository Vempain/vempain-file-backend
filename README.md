[![Dependabot Updates](https://github.com/Vempain/vempain-file-backend/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/Vempain/vempain-file-backend/actions/workflows/dependabot/dependabot-updates)
[![CodeQL](https://github.com/Vempain/vempain-file-backend/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/Vempain/vempain-file-backend/actions/workflows/github-code-scanning/codeql)
[![CI](https://github.com/Vempain/vempain-file-backend/actions/workflows/ci.yaml/badge.svg)](https://github.com/Vempain/vempain-file-backend/actions/workflows/ci.yaml)
![GitHub Tag](https://img.shields.io/github/v/tag/Vempain/vempain-file-backend)
![GitHub License](https://img.shields.io/github/license/Vempain/vempain-file-backend?color=green)

# Vempain File service

The service is part of the [Vempain](https://vempain.poltsi.fi/) project. The file service maintains a database of original files, their metadata as well as
tracks any exported files (such as jpg-files converted from the original RAW files). The service also forms general notion of file groups based on the
directories in which the files are placed, however other groupings can also be created. The main purpose of the service is metadata management and file export.

The service can be used in conjunction with the Vempain Admin as well as Vempain Simplex to handle document flows from source to web.

Service exposes REST APIs designed to be used by the Vempain File frontend.

## CLI location

The Vempain CLI code was moved out of this repository into a dedicated repository:

- `vempain-cli`

This backend repository now contains only the File backend API (`api/`) and service implementation (`service/`).

## Scheduled refresh for modified files

The service now includes a scheduler that revisits already-registered source files and refreshes their DB data when the filesystem timestamp indicates updates.

- Only files already present in the `files` table are considered.
- Last run state is persisted in `scheduler_checkpoint` (`task_name=updated_file_refresh`).
- On first run (no checkpoint yet), all DB-known files are treated as candidates.
- For candidate files, refresh is done only when SHA-256 differs from the stored value.
- Linked `export_files` rows are refreshed, and site file refresh is attempted for files known to be published in admin.

Configuration:

```yaml
vempain:
  refresh-updated-files:
    enabled: true
    cron: "0 */10 * * * *"
```

## Video export queue

Newly imported video files without an entry in `export_files` are recorded in
`file_processing_queue`. A scheduled worker processes queued files concurrently every 15 minutes by default. The video encoder uses JavaCV and bundled FFmpeg
native libraries; no host FFmpeg installation is required. `exiftool` remains required for scanning source metadata.

The output can be configured in the active Spring profile:

```yaml
vempain:
    file-processing:
        cron: "0 */15 * * * *"
        video:
            width: 1280
            height: 720
            fps: 30
            audio-bitrate: 128000
            audio-codec: vorbis
            video-codec: mpeg4
            container: mp4
            quality: 23
```

The default video codec is the JavaCV-supported `mpeg4` encoder, the default audio codec is Ogg Vorbis, and the default container is MP4. In configuration, use
`vorbis` for the Ogg audio codec because `ogg` is a container format, not an FFmpeg audio encoder name. The bundled JavaCV FFmpeg runtime does not expose a
Theora encoder or an OGV-compatible video encoder, so Theora/OGV cannot safely be the default without replacing the native runtime or invoking the host `ffmpeg`
executable. OGV requires a Theora video stream; pairing OGV with the bundled MPEG-4 encoder fails with `Unsupported codec id in stream 0`.

Missing video exports are rediscovered by a scheduled job, so files imported while queue discovery is unavailable are eventually queued:

```yaml
vempain:
    queue-missing-video-exports:
        batch-size: 1000
        worker-count: 4
        enabled: true
        cron: "0 0 * * * *"
```

## File grouping

The default way to group files is based on the directory structure. Each directory forms a file group and all files within that directory belong to that group.
This is the default behavior when files are uploaded via Vempain Simplex or Vempain Admin and is also the way which requires the least amount of of effort to
maintain. Thus, the recommended way to store the files is to place them in leaf directories, i.e. directories that do not contain any other directories and
also grouped on higher level according to relatedness.

A clarifying example where the upper level directories group related files together:

```
Animals/
    Cats/
      Cats_On_The_Beach/
         cat_beach1.raw
         cat_beach2.raw
      Cats_At_Home/
         cat_home1.raw
         cat_home2.raw
    Dogs/
        Dogs_In_The_Park/
            dog_park1.raw
            dog_park2.raw
        Dogs_At_Home/
            dog_home1.raw
            dog_home2.raw
```

## Swagger API

[Local UI](http://localhost:8081/actuator/swagger-ui/index.html)

## How to build

In order to build the project, you need to have Java 21 and Git installed on your machine. Begin with cloning the repository. After that run the following
command in the root directory of the project:

```bash
./gradlew clean test \
bootJar bootRun \
--args="--spring.profiles.active=local --vempain.app.frontend-url=http://localhost:3000,http://localhost:8081" \
2>&1 | tee /tmp/out-vempain_file.log
```

This will build the project, run the tests, create a JAR file, and start the application with the `local` profile. The output will be logged to
`/tmp/out-vempain_file.log`.

Note that the `vempain.app.frontend-url` argument is used to specify the frontend URL for the application. You can change it according to your needs. You need
to include the `http://localhost:8081` if you want to use the Swagger UI.

[AGENTS.md](docs/AGENTS.md) has more detailed orientation and workflow guidance for agents working in this codebase.
