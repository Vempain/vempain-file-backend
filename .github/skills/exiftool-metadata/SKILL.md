---
name: exiftool-metadata
description: Read or write file metadata with ExifTool in the file backend (MetadataTool, -g1 group names, description and subject extraction, rewriting values, refreshing database rows). Use when a change touches metadata extraction, tags written into files or the metadata table.
---

# ExifTool metadata in vempain-file-backend

- `fi.poltsi.vempain.file.tools.MetadataTool` is the only place that runs `exiftool`. Extraction uses
  `exiftool -a -u -ee -api RequestAll=3 -g1 -J`, so JSON groups are family 1 names: `IFD0`, `ExifIFD`, `XMP-dc`, `XMP-lr`, `IPTC`,
  `Composite`, `File`. There is no `XMP` group in that output; never look a value up under it.
- The scanner (`DirectoryProcessorService.processMetadata`) stores every group/key/value of that JSON in the `metadata` table (one row
  per file and key) and derives `files.description` with `MetadataTool.extractDescription` (`XMP-dc:Description`, then
  `IFD0:ImageDescription`, then `IPTC:Caption-Abstract`). Thumbnails copy the description of the file they were made from.
- Writing: `writeSubjects(file, subjects)` rewrites the tag locations; `writeMetadataValue(file, group, key, value)` writes one
  `-GROUP:KEY=value` (empty value removes the entry). Group and key must pass `isValidTagName` (`[A-Za-z0-9][A-Za-z0-9_-]*`) and the
  group must be writable (`ExifTool`, `File`, `System`, `Composite` are read only). Both retry with `-m` on the MakerNotes warning.
- After rewriting an original: write the same change into the export file (`ExportFileRepository.findByFileId`), update its size and
  SHA-256, regenerate the thumbnail, then call `DirectoryProcessorService.refreshExistingOriginalFile(file, original)` so the `files`
  row and the `metadata` rows follow the file content; set `modifier`/`modified`. `TagService.mutateFile` and
  `MetadataService.rewriteFile` are the reference implementations.
- Bulk rewrites always run as background tasks with one step per file and a per-file compensation that writes the old value back.
- Tests: `MetadataToolUTC` (JSON fixtures, no exiftool), `MetadataToolITC` (real JPEG written with `ImageIO`, exiftool on the host),
  controller tests end to end with `@TempDir` under `/tmp` (the CTC original root). Exiftool must be installed where tests run.
