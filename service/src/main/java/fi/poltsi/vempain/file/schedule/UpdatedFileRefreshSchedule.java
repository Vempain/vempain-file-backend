package fi.poltsi.vempain.file.schedule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import fi.poltsi.vempain.admin.api.response.file.FileIngestSiteFileAclResponse;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.common.metadata.ExifToolService;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.SchedulerCheckpointEntity;
import fi.poltsi.vempain.file.metrics.FileMetrics;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.SchedulerCheckpointRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.service.DirectoryProcessorService;
import fi.poltsi.vempain.file.service.PublishService;
import fi.poltsi.vempain.file.service.VempainAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatedFileRefreshSchedule {

    private static final String TASK_NAME = "updated_file_refresh";

    private final FileRepository fileRepository;
    private final ExportFileRepository exportFileRepository;
    private final SchedulerCheckpointRepository schedulerCheckpointRepository;
    private final DirectoryProcessorService directoryProcessorService;
    private final PublishService publishService;
    private final VempainAdminService vempainAdminService;
    private final FileMetrics fileMetrics;
    private final ExifToolService exifToolService;

    @Value("${vempain.refresh-updated-files.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${vempain.original-root-directory}")
    private String originalRootDirectory;

    @Value("${vempain.export-root-directory}")
    private String exportRootDirectory;

    @Value("${vempain.scheduling.enabled:true}")
    private boolean schedulingEnabled = true;

    @Scheduled(cron = "${vempain.refresh-updated-files.cron:0 */10 * * * *}")
    public void refreshUpdatedFilesScheduled() {
        if (!schedulingEnabled
            || !schedulerEnabled) {
            return;
        }
        runRefresh();
    }

    public void runRefresh() {
        var runStartedAt = Instant.now();
        var checkpoint = schedulerCheckpointRepository.findById(TASK_NAME)
            .orElse(null);
        var lastCheckAt = checkpoint != null ? checkpoint.getLastChecked() : Instant.EPOCH;
        var firstRun = checkpoint == null;

        log.info("Starting updated file refresh. firstRun={}, lastCheckAt={}", firstRun, lastCheckAt);

        for (var fileEntity : fileRepository.findAll()) {
            processSingleFile(fileEntity, lastCheckAt, firstRun);
        }

        schedulerCheckpointRepository.save(SchedulerCheckpointEntity.builder()
            .taskName(TASK_NAME)
            .lastChecked(runStartedAt)
            .build());
        fileMetrics.setRefreshCheckpointAgeSeconds(0);
        log.info("Updated file refresh finished. New checkpoint={}", runStartedAt);
    }

    private void processSingleFile(FileEntity fileEntity, Instant lastCheckAt, boolean firstRun) {
        var sourcePath = resolveOriginalPath(fileEntity);
        if (!Files.exists(sourcePath)) {
            return;
        }

        repairSiteFilePublication(fileEntity);
        var sitePublished = fileEntity.getSiteFilePublished();

        try {
            var modifiedAt = Files.getLastModifiedTime(sourcePath)
                .toInstant();
            if (!firstRun
                && !modifiedAt.isAfter(lastCheckAt)) {
                return;
            }
        } catch (Exception e) {
            log.warn("Unable to read last-modified time for file {}", sourcePath, e);
            return;
        }

        fileMetrics.refreshVerified();
        var currentSha = computeSha256(sourcePath.toFile());
        if (currentSha == null
            || Objects.equals(currentSha, fileEntity.getSha256sum())) {
            return;
        }

        log.info("Refreshing updated file id={} path={}", fileEntity.getId(), sourcePath);
        try {
            var updated = directoryProcessorService.refreshExistingOriginalFile(fileEntity, sourcePath.toFile());
            if (!updated) {
                return;
            }
            refreshLinkedExport(fileEntity.getId());
            if (Boolean.TRUE.equals(sitePublished)) {
                var refreshedEntity = fileRepository.findById(fileEntity.getId())
                    .orElse(fileEntity);
                if (publishService.republishSiteFile(refreshedEntity)) {
                    fileMetrics.refreshRepublished();
                }
            }
        } catch (Exception e) {
            log.error("Failed to refresh updated file id={} path={}", fileEntity.getId(), sourcePath, e);
        }
    }

    private void repairSiteFilePublication(FileEntity fileEntity) {
        if (fileEntity.getFileType() == FileTypeEnum.UNKNOWN) {
            if (fileEntity.getSiteFilePublished() == null) {
                fileEntity.setSiteFilePublished(false);
                fileRepository.save(fileEntity);
            }
            return;
        }

        if (Boolean.TRUE.equals(fileEntity.getSiteFilePublished())
            && fileEntity.getSiteFileId() != null) {
            return;
        }

        try {
            var siteFile = vempainAdminService.getSiteFileAclsByFileId(fileEntity.getId());
            if (siteFile.isPresent()) {
                updateSiteFilePublication(fileEntity, siteFile.get());
            } else if (fileEntity.getSiteFilePublished() == null) {
                fileEntity.setSiteFilePublished(false);
                fileRepository.save(fileEntity);
            }
        } catch (VempainAuthenticationException | ResponseStatusException e) {
            log.warn("Unable to verify site publication for file id {}", fileEntity.getId(), e);
        }
    }

    private void updateSiteFilePublication(FileEntity fileEntity, FileIngestSiteFileAclResponse siteFile) {
        fileEntity.setSiteFileId(siteFile.getSiteFileId());
        fileEntity.setSiteFilePublished(true);
        fileRepository.save(fileEntity);
    }

    private void refreshLinkedExport(Long fileId) {
        var optionalExport = exportFileRepository.findByFileId(fileId);
        if (optionalExport.isEmpty()) {
            return;
        }

        var exportFile = optionalExport.get();
        var exportPath = resolveExportPath(exportFile);
        if (!Files.exists(exportPath)) {
            return;
        }

        var sha = computeSha256(exportPath.toFile());
        if (sha == null) {
            return;
        }

        try {
            exportFile.setSha256sum(sha);
            exportFile.setFilesize(Files.size(exportPath));
            // a single-tag read is enough for the MIME type; an empty answer keeps the stored type
            var mimetype = exifToolService.readTag(exportPath.toFile(), "MIMEType");
            if (mimetype != null
                && !mimetype.isBlank()) {
                exportFile.setMimetype(mimetype);
            }
            exportFile.setOriginalDocumentId(exportFile.getFile()
                .getOriginalDocumentId());
            exportFileRepository.save(exportFile);
        } catch (Exception e) {
            log.warn("Failed to refresh linked export file for file id {}", fileId, e);
        }
    }

    private Path resolveOriginalPath(FileEntity fileEntity) {
        var relativePath = normalizePath(fileEntity.getFilePath());
        return Path.of(originalRootDirectory)
            .resolve(relativePath)
            .resolve(fileEntity.getFilename());
    }

    private Path resolveExportPath(ExportFileEntity exportFileEntity) {
        var relativePath = normalizePath(exportFileEntity.getFilePath());
        return Path.of(exportRootDirectory)
            .resolve(relativePath)
            .resolve(exportFileEntity.getFilename());
    }

    private String normalizePath(String value) {
        if (value == null
            || value.isBlank()
            || "/".equals(value)) {
            return "";
        }
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
