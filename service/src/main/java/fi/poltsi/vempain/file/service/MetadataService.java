package fi.poltsi.vempain.file.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import jakarta.persistence.criteria.Predicate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.auth.tools.AuthTools;
import fi.poltsi.vempain.common.task.TaskProgress;
import fi.poltsi.vempain.common.task.TaskRunner;
import fi.poltsi.vempain.file.api.TaskTypeEnum;
import fi.poltsi.vempain.file.api.request.MetadataFilesRequest;
import fi.poltsi.vempain.file.api.request.MetadataSearchRequest;
import fi.poltsi.vempain.file.api.request.MetadataValueUpdateRequest;
import fi.poltsi.vempain.file.api.response.MetadataSummaryResponse;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.MetadataEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.repository.files.ThumbFileRepository;
import fi.poltsi.vempain.file.service.files.FileSearchHelper;
import fi.poltsi.vempain.file.tools.FileTool;
import fi.poltsi.vempain.file.tools.LikePatterns;
import fi.poltsi.vempain.file.tools.MetadataTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Browses the {@code metadata} table (one row per file, ExifTool group and key) and rewrites a value across every file carrying it. Metadata rows carry no ACL:
 * the file lists are limited to files the caller may read and a rewrite requires the modify privilege on every affected file, checked synchronously before the
 * background task is submitted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataService {

    static final int MAX_PAGE_SIZE = 200;

    private final MetadataRepository metadataRepository;
    private final FileRepository fileRepository;
    private final FileResponseEnricher fileResponseEnricher;
    private final ExportFileRepository exportFileRepository;
    private final ThumbFileRepository thumbFileRepository;
    private final DirectoryProcessorService directoryProcessorService;
    private final ThumbnailGenerationService thumbnailGenerationService;
    private final FileAclService fileAclService;
    private final TaskRunner taskRunner;
    private final ApplicationContext applicationContext;

    @Value("${vempain.original-root-directory}")
    private String originalRootDirectory;
    @Value("${vempain.export-root-directory}")
    private String exportRootDirectory;
    @Value("${vempain.generate-missing-thumbnails.thumb-image-size}")
    private int thumbnailMinimumSize;
    @Value("${vempain.generate-missing-thumbnails.thumb-image-quality}")
    private float thumbnailQuality;

    /**
     * Distinct group, key and value rows with their file counts. The counts are system wide (metadata has no ACL); only the file lists of {@link #findFiles}
     * are ACL filtered.
     */
    @Transactional(readOnly = true)
    public PagedResponse<MetadataSummaryResponse> findSummaries(MetadataSearchRequest request) {
        var page = Math.max(0, request.getPage());
        var size = Math.clamp(request.getSize(), 1, MAX_PAGE_SIZE);
        var pageable = PageRequest.of(page, size, buildSummarySort(request.getSortBy(), request.getDirection()));
        var result = metadataRepository.searchSummaries(request.getMetadataGroup(), request.getMetadataKey(), request.getMetadataValue(),
                Boolean.TRUE.equals(request.getCaseSensitive()), pageable);
        var content = result.getContent()
            .stream()
            .map(row -> MetadataSummaryResponse.builder()
                .metadataGroup(row.metadataGroup())
                .metadataKey(row.metadataKey())
                .metadataValue(row.metadataValue())
                .fileCount(row.fileCount())
                .writable(MetadataTool.isWritableGroup(row.metadataGroup())
                          && MetadataTool.isValidTagName(row.metadataGroup())
                          && MetadataTool.isValidTagName(row.metadataKey()))
                .build())
            .toList();
        return PagedResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), result.isFirst(),
                result.isLast());
    }

    /**
     * Files the caller may read that carry the given group, key and value, optionally narrowed by file name and type.
     */
    @Transactional(readOnly = true)
    public PagedResponse<FileResponse> findFiles(MetadataFilesRequest request) {
        var page = Math.max(0, request.getPage());
        var size = Math.clamp(request.getSize(), 1, MAX_PAGE_SIZE);
        var sort = FileSearchHelper.buildSort(request.getSortBy(), request.getDirection());

        Specification<FileEntity> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            var metadata = query.subquery(Long.class);
            var row = metadata.from(MetadataEntity.class);
            metadata.select(row.get("id"))
                .where(cb.equal(row.get("file"), root), cb.equal(row.get("metadataGroup"), request.getMetadataGroup()),
                        cb.equal(row.get("metadataKey"), request.getMetadataKey()), cb.equal(row.get("metadataValue"), request.getMetadataValue()));
            predicates.add(cb.exists(metadata));
            if (request.getFileType() != null) {
                predicates.add(cb.equal(root.get("fileType"), request.getFileType()));
            }
            if (request.getFilename() != null
                && !request.getFilename()
                    .isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("filename")), LikePatterns.containsIgnoreCase(request.getFilename()
                    .trim()), LikePatterns.ESCAPE_CHAR));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        var result = fileRepository.findAll(fileAclService.readableFiles(specification), PageRequest.of(page, size, sort));
        var content = fileResponseEnricher.<FileResponse>toResponses(result.getContent());
        return PagedResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), result.isFirst(),
                result.isLast());
    }

    /**
     * Rewrites the value in every file carrying the group, key and current value as a background task (one step per file). Validation and the ACL checks run
     * synchronously: a read-only group or an unknown value answers 400, a file the caller may not modify 403.
     */
    public TaskProgress updateValueAsTask(MetadataValueUpdateRequest request) {
        requireWritable(request.getMetadataGroup(), request.getMetadataKey());
        var fileIds = metadataRepository.findFileIdsByGroupKeyAndValue(request.getMetadataGroup(), request.getMetadataKey(), request.getMetadataValue());
        if (fileIds.isEmpty()) {
            throw new IllegalArgumentException("No file carries the metadata value");
        }
        fileAclService.requireModifyBulk(fileRepository.findAllById(new LinkedHashSet<>(fileIds)));

        var newValue = request.getNewValue() == null ? "" : request.getNewValue();
        var title = (newValue.isEmpty() ? "Remove " : "Rewrite ")
                + request.getMetadataGroup()
                + ":"
                + request.getMetadataKey()
                + " in "
                + fileIds.size()
                + " files";
        var payload = Map.of("request", request, "file_ids", fileIds);
        var proxy = applicationContext.getBean(MetadataService.class);
        return taskRunner.submitDurable(TaskTypeEnum.METADATA_UPDATE_VALUE.name(), title, fileIds.size(), payload, progress -> {
            proxy.applyValueUpdate(request, fileIds, progress);
            return null;
        });
    }

    /**
     * Durable worker entry point.
     */
    public void applyValueUpdateForTask(MetadataValueUpdateRequest request, List<Long> fileIds, TaskProgress progress) {
        applicationContext.getBean(MetadataService.class)
            .applyValueUpdate(request, fileIds, progress);
    }

    /**
     * Transactional body of the {@code METADATA_UPDATE_VALUE} task: rewrites the files one by one and registers the undo of each rewrite right after it.
     */
    @Transactional
    public void applyValueUpdate(MetadataValueUpdateRequest request, List<Long> fileIds, TaskProgress progress) {
        var group = request.getMetadataGroup();
        var key = request.getMetadataKey();
        var newValue = request.getNewValue() == null ? "" : request.getNewValue();
        var proxy = applicationContext.getBean(MetadataService.class);
        for (var file : fileRepository.findAllById(new LinkedHashSet<>(fileIds))) {
            progress.checkpoint();
            var fileId = file.getId();
            var oldValue = metadataRepository.findFirstByFileIdAndMetadataGroupAndMetadataKey(fileId, group, key)
                .map(MetadataEntity::getMetadataValue)
                .orElse(request.getMetadataValue());
            var previous = oldValue == null ? "" : oldValue;
            rewriteFile(file, group, key, newValue);
            var description = "Restore " + group + ":" + key + " of " + file.getFilename();
            if (progress.isDurable()) {
                progress.registerDurableCompensation(description, "METADATA_REVERT_VALUE",
                        Map.of("file_id", fileId, "metadata_group", group, "metadata_key", key, "old_value", previous));
            } else {
                progress.registerCompensation(description, () -> proxy.revertValue(fileId, group, key, previous));
            }
            progress.advance("Updated " + file.getFilename());
        }
    }

    /**
     * Undo of one file's rewrite made by a {@code METADATA_UPDATE_VALUE} task: writes the previous value back.
     */
    @Transactional
    public void revertValue(long fileId, String group, String key, String oldValue) {
        fileRepository.findById(fileId)
            .ifPresent(file -> rewriteFile(file, group, key, oldValue));
    }

    private void requireWritable(String group, String key) {
        if (!MetadataTool.isValidTagName(group)
            || !MetadataTool.isValidTagName(key)) {
            throw new IllegalArgumentException("Invalid metadata group or key");
        }
        if (!MetadataTool.isWritableGroup(group)) {
            throw new IllegalArgumentException("Metadata group '" + group + "' is read-only");
        }
    }

    /**
     * Writes the value into the original file and its export derivative, refreshes the derived data (checksums, thumbnail) and re-reads the file so that the
     * {@code files} and {@code metadata} rows follow the file content.
     */
    private void rewriteFile(FileEntity file, String group, String key, String value) {
        try {
            var original = resolve(originalRootDirectory, file.getFilePath(), file.getFilename());
            if (!Files.isRegularFile(original.toPath())) {
                throw new IOException("Original file does not exist: " + original);
            }
            MetadataTool.writeMetadataValue(original, group, key, value);

            var export = exportFileRepository.findByFileId(file.getId())
                .orElse(null);
            if (export != null) {
                var exported = resolve(exportRootDirectory, export.getFilePath(), export.getFilename());
                if (Files.isRegularFile(exported.toPath())) {
                    MetadataTool.writeMetadataValue(exported, group, key, value);
                    export.setFilesize(exported.length());
                    export.setSha256sum(FileTool.computeSha256(exported));
                    exportFileRepository.save(export);
                    regenerateThumbnail(export);
                }
            }
            directoryProcessorService.refreshExistingOriginalFile(file, original);
            file.setModifier(AuthTools.getCurrentUserId());
            file.setModified(Instant.now());
            fileRepository.save(file);
        } catch (IOException | VempainAuthenticationException e) {
            throw new IllegalStateException("Failed to update metadata " + group + ":" + key + " of file " + file.getId(), e);
        }
    }

    private void regenerateThumbnail(ExportFileEntity export) throws IOException {
        var thumbnailId = thumbFileRepository.findThumbnailIdByTargetFileId(export.getFile()
            .getId());
        if (thumbnailId.isPresent()) {
            var thumbnail = thumbFileRepository.findById(thumbnailId.get())
                .orElse(null);
            if (thumbnail != null) {
                var path = resolve(originalRootDirectory, thumbnail.getFilePath(), thumbnail.getFilename());
                Files.deleteIfExists(path.toPath());
                thumbFileRepository.delete(thumbnail);
            }
        }
        try {
            thumbnailGenerationService.generateThumbnail(export, originalRootDirectory, exportRootDirectory, thumbnailMinimumSize, thumbnailQuality);
        } catch (Exception e) {
            throw new IOException("Failed to regenerate thumbnail", e);
        }
    }

    private File resolve(String root, String path, String filename) {
        return Path.of(root)
            .resolve((path == null ? "" : path).replaceFirst("^/", ""))
            .resolve(filename)
            .toFile();
    }

    private Sort buildSummarySort(String sortBy, Sort.Direction direction) {
        var property = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
            case "metadata_group", "group" -> "metadata_group";
            case "metadata_value", "value" -> "metadata_value";
            case "file_count" -> "file_count";
            default -> "metadata_key";
        };
        return Sort.by(direction == null ? Sort.Direction.ASC : direction, property);
    }
}
