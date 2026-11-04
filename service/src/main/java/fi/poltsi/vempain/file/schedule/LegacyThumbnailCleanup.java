package fi.poltsi.vempain.file.schedule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fi.poltsi.vempain.file.entity.FileThumbnailEntity;
import fi.poltsi.vempain.file.metrics.FileMetrics;
import fi.poltsi.vempain.file.repository.FileThumbnailRepository;
import fi.poltsi.vempain.file.tools.FileTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Removes filesystem remnants left by the legacy thumbnail-as-file model after V1014. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyThumbnailCleanup {

    private final JdbcTemplate jdbcTemplate;
    private final FileThumbnailRepository thumbnailRepository;
    private final FileMetrics fileMetrics;

    @Value("${vempain.original-root-directory}")
    private String originalRootDirectory;

    @Value("${vempain.legacy-thumbnail-cleanup.enabled:true}")
    private boolean enabled;

    @Value("${vempain.scheduling.enabled:true}")
    private boolean schedulingEnabled;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanup() {
        if (!enabled
            || !schedulingEnabled) {
            return;
        }
        try {
            var result = cleanupOrphans();
            result = result.plus(removeMissingThumbnails());
            removeEmptyDirectories();
            fileMetrics.legacyCleanup(result.skippedMismatches() == 0);
            log.info("Legacy thumbnail cleanup finished: deleted_files={}, skipped_mismatch={}, rows_removed={}", result.deletedFiles(),
                    result.skippedMismatches(), result.rowsRemoved());
        } catch (RuntimeException e) {
            fileMetrics.legacyCleanup(false);
            log.error("Legacy thumbnail cleanup failed", e);
        }
    }

    private CleanupResult cleanupOrphans() {
        var root = realRoot();
        var generatedRoot = root.resolve("thumb")
            .normalize();
        var rows = jdbcTemplate.queryForList("SELECT id, file_path, filename, sha256sum FROM legacy_thumbnail_orphans ORDER BY id LIMIT 500");
        var result = new CleanupResult(0, 0, 0);
        for (Map<String, Object> row : rows) {
            var path = resolve(root, generatedRoot, String.valueOf(row.get("file_path")), String.valueOf(row.get("filename")));
            if (path == null) {
                log.warn("Skipping legacy thumbnail outside generated root: {}/{}", row.get("file_path"), row.get("filename"));
                result = result.plus(new CleanupResult(0, 1, 0));
                continue;
            }
            if (Files.isRegularFile(path)
                && !String.valueOf(row.get("sha256sum"))
                    .equals(FileTool.computeSha256(path.toFile()))) {
                log.warn("Skipping legacy thumbnail with checksum mismatch: {}", path);
                result = result.plus(new CleanupResult(0, 1, 0));
                continue;
            }
            try {
                var deleted = Files.deleteIfExists(path);
                jdbcTemplate.update("DELETE FROM legacy_thumbnail_orphans WHERE id = ?", row.get("id"));
                result = result.plus(new CleanupResult(deleted ? 1 : 0, 0, 1));
            } catch (IOException e) {
                log.warn("Could not remove legacy thumbnail {}", path, e);
            }
        }
        return result;
    }

    private CleanupResult removeMissingThumbnails() {
        var result = new CleanupResult(0, 0, 0);
        for (FileThumbnailEntity thumbnail : thumbnailRepository.findAll()) {
            var path = resolve(realRoot(), realRoot().resolve("thumb")
                .normalize(), thumbnail.getFilePath(), thumbnail.getFilename());
            if (path != null
                && Files.isRegularFile(path)) {
                continue;
            }
            thumbnailRepository.delete(thumbnail);
            result = result.plus(new CleanupResult(0, 0, 1));
        }
        return result;
    }

    private Path resolve(Path root, Path generatedRoot, String filePath, String filename) {
        var path = root.resolve(stripLeadingSlash(filePath))
            .resolve(filename)
            .normalize();
        return path.startsWith(generatedRoot) ? path : null;
    }

    private Path realRoot() {
        return Path.of(originalRootDirectory)
            .toAbsolutePath()
            .normalize();
    }

    private String stripLeadingSlash(String value) {
        var result = value == null ? "" : value.replace('\\', '/');
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private void removeEmptyDirectories() {
        var generatedRoot = realRoot().resolve("thumb");
        if (!Files.isDirectory(generatedRoot)) {
            return;
        }
        try (var paths = Files.walk(generatedRoot)) {
            paths.sorted(Comparator.reverseOrder())
                .filter(Files::isDirectory)
                .filter(path -> !path.equals(generatedRoot))
                .forEach(path -> {
                    try (var children = Files.list(path)) {
                        if (children.findAny()
                            .isEmpty()) {
                            Files.deleteIfExists(path);
                        }
                    } catch (IOException e) {
                        log.debug("Could not remove empty thumbnail directory {}", path, e);
                    }
                });
        } catch (IOException e) {
            log.debug("Could not enumerate thumbnail directories", e);
        }
    }

    private record CleanupResult(long deletedFiles, long skippedMismatches, long rowsRemoved) {

        CleanupResult plus(CleanupResult other) {
            return new CleanupResult(deletedFiles + other.deletedFiles, skippedMismatches + other.skippedMismatches, rowsRemoved + other.rowsRemoved);
        }
    }
}
