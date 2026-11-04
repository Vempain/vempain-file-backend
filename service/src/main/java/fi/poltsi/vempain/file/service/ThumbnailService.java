package fi.poltsi.vempain.file.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import fi.poltsi.vempain.file.api.response.files.FileThumbnailResponse;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileThumbnailEntity;
import fi.poltsi.vempain.file.repository.FileThumbnailRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final FileRepository fileRepository;
    private final FileThumbnailRepository thumbnailRepository;
    private final FileAclService fileAclService;
    private final ThumbnailGenerationService thumbnailGenerationService;

    @Value("${vempain.original-root-directory}")
    private String originalRootDirectory;

    @Value("${vempain.export-root-directory}")
    private String exportRootDirectory;

    @Value("${vempain.generate-missing-thumbnails.thumb-image-size}")
    private int thumbnailMinimumSize;

    @Value("${vempain.generate-missing-thumbnails.thumb-image-quality}")
    private float thumbnailQuality;

    @Transactional(readOnly = true)
    public Optional<FileThumbnailResponse> findByFileId(long fileId) {
        var file = requireFile(fileId);
        fileAclService.requireRead(file);
        return thumbnailRepository.findByFileId(fileId)
            .map(FileThumbnailEntity::toResponse);
    }

    @Transactional(readOnly = true)
    public ContentFile resolveContent(long fileId) {
        var file = requireFile(fileId);
        fileAclService.requireRead(file);
        var thumbnail = thumbnailRepository.findByFileId(fileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thumbnail for file %d not found".formatted(fileId)));
        var rootPath = realRoot();
        var resolvedPath = rootPath.resolve(normalizeRelativeDirectory(thumbnail.getFilePath()))
            .resolve(thumbnail.getFilename())
            .normalize();
        if (!resolvedPath.startsWith(rootPath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolved thumbnail path is outside configured original root");
        }
        final Path realPath;
        try {
            realPath = resolvedPath.toRealPath();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thumbnail content for file %d not found from storage".formatted(fileId));
        }
        if (!realPath.startsWith(rootPath)
            || !Files.isRegularFile(realPath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolved thumbnail path is outside configured original root");
        }
        try {
            return new ContentFile(realPath, thumbnail.getFilename(), thumbnail.getMimetype(), Files.size(realPath));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to resolve thumbnail size for file %d".formatted(fileId), e);
        }
    }

    @Transactional
    public HttpStatus delete(long fileId) {
        var file = requireFile(fileId);
        fileAclService.requireDelete(file);
        var thumbnail = thumbnailRepository.findByFileId(fileId)
            .orElse(null);
        if (thumbnail == null) {
            return HttpStatus.NOT_FOUND;
        }
        deleteContent(thumbnail);
        thumbnailRepository.delete(thumbnail);
        return HttpStatus.OK;
    }

    @Transactional
    public void regenerate(ExportFileEntity export) throws IOException {
        var thumbnail = thumbnailRepository.findByFileId(export.getFile()
            .getId())
            .orElse(null);
        if (thumbnail != null) {
            deleteContent(thumbnail);
            thumbnailRepository.delete(thumbnail);
        }
        thumbnailGenerationService.generateThumbnail(export, originalRootDirectory, exportRootDirectory, thumbnailMinimumSize, thumbnailQuality);
    }

    @Transactional
    public void deleteForFile(FileEntity file) {
        thumbnailRepository.findByFileId(file.getId())
            .ifPresent(thumbnail -> {
                deleteContent(thumbnail);
                thumbnailRepository.delete(thumbnail);
            });
    }

    private FileEntity requireFile(long fileId) {
        return fileRepository.findById(fileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File with id %d not found".formatted(fileId)));
    }

    private void deleteContent(FileThumbnailEntity thumbnail) {
        try {
            var root = realRoot();
            var path = root.resolve(normalizeRelativeDirectory(thumbnail.getFilePath()))
                .resolve(thumbnail.getFilename())
                .normalize();
            if (!path.startsWith(root)) {
                log.warn("Refusing to remove thumbnail outside original root: {} / {}", thumbnail.getFilePath(), thumbnail.getFilename());
                return;
            }
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not remove thumbnail file {} / {}", thumbnail.getFilePath(), thumbnail.getFilename(), e);
        }
    }

    private Path realRoot() {
        try {
            return Paths.get(originalRootDirectory)
                .toRealPath();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Configured original file storage is unavailable");
        }
    }

    private String normalizeRelativeDirectory(String value) {
        if (value == null
            || value.isBlank()
            || "/".equals(value.trim())) {
            return "";
        }
        var normalized = value.trim()
            .replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    public record ContentFile(Path absolutePath, String filename, String mimetype, long size) {}
}
