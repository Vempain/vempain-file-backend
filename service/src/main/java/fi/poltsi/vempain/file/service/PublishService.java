package fi.poltsi.vempain.file.service;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import fi.poltsi.vempain.admin.api.request.file.FileIngestAclRequest;
import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.common.api.response.LocationResponse;
import fi.poltsi.vempain.common.task.TaskProgress;
import fi.poltsi.vempain.common.task.TaskRunner;
import fi.poltsi.vempain.file.api.TaskTypeEnum;
import fi.poltsi.vempain.file.api.request.PublishAclRequest;
import fi.poltsi.vempain.file.api.request.PublishAllFileGroupsRequest;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.request.PublishFileRequest;
import fi.poltsi.vempain.file.api.request.PublishFilesRequest;
import fi.poltsi.vempain.file.api.response.CopyrightResponse;
import fi.poltsi.vempain.file.api.response.PublishUnitResponse;
import fi.poltsi.vempain.file.api.response.PublishUserResponse;
import fi.poltsi.vempain.file.entity.AudioFileEntity;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.metrics.FileMetrics;
import fi.poltsi.vempain.file.metrics.FileMetrics.PublishResult;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
import fi.poltsi.vempain.file.tools.MetadataTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;
import static fi.poltsi.vempain.file.tools.MetadataTool.collectStandardMetadataAsJson;

/**
 * Publishes file groups to the admin backend. Publishing runs as background tasks ({@link TaskRunner}); the caller gets the task id back immediately and
 * follows the progress through the task API.
 */
@Slf4j
@RequiredArgsConstructor
@Service
@SuppressWarnings("checkstyle:OverloadMethodsDeclarationOrder")
public class PublishService {

    private final FileGroupRepository fileGroupRepository;
    private final FileRepository fileRepository;
    private final ExportFileRepository exportFileRepository;

    private final VempainAdminService vempainAdminService;
    private final TagService tagService;
    private final LocationService locationService;
    private final FileAclService fileAclService;

    private final ImageTool imageTool;
    private final ApplicationContext applicationContext;
    private final TaskRunner taskRunner;
    private final MetadataService metadataService;
    private final FileMetrics fileMetrics;

    @Value("${vempain.site-image-size:1200}")
    private int siteImageSize;

    @Value("${vempain.export-root-directory}")
    private String exportRootDirectory;

    @Value("${vempain.export-file-type}")
    private String exportFileType;

    /**
     * The admin users a publish may grant privileges to, fetched from the admin backend with the service-to-service API token.
     *
     * @throws VempainAuthenticationException when the admin backend refuses the token
     */
    public List<PublishUserResponse> listPublishUsers() {
        return vempainAdminService.listIngestUsers()
            .stream()
            .map(user -> PublishUserResponse.builder()
                .id(user.getId())
                .loginName(user.getLoginName())
                .name(user.getName())
                .nick(user.getNick())
                .build())
            .toList();
    }

    /**
     * The admin units a publish may grant privileges to, fetched from the admin backend like the users.
     *
     * @throws VempainAuthenticationException when the admin backend refuses the token
     */
    public List<PublishUnitResponse> listPublishUnits() {
        return vempainAdminService.listIngestUnits()
            .stream()
            .map(unit -> PublishUnitResponse.builder()
                .id(unit.getId())
                .name(unit.getName())
                .description(unit.getDescription())
                .build())
            .toList();
    }

    /**
     * Maps the additional grantees of a publish request to the ingest contract of the admin backend; null when none were requested.
     */
    static List<FileIngestAclRequest> toIngestAcls(List<PublishAclRequest> acls) {
        if (acls == null
            || acls.isEmpty()) {
            return null;
        }

        return acls.stream()
            .map(acl -> FileIngestAclRequest.builder()
                .userId(acl.getUserId())
                .unitId(acl.getUnitId())
                .readPrivilege(acl.isReadPrivilege())
                .createPrivilege(acl.isCreatePrivilege())
                .modifyPrivilege(acl.isModifyPrivilege())
                .deletePrivilege(acl.isDeletePrivilege())
                .build())
            .toList();
    }

    /**
     * Publishing a file group sends every file of the group to the admin backend, so the caller must hold the modify privilege on every file in the group. This
     * runs synchronously in the caller's request so that a denial fails the request itself instead of the background task.
     *
     * @throws AccessDeniedException when at least one file of the group is not modifiable by the current user
     */
    @Transactional(readOnly = true)
    public void authorizeFileGroupPublish(long fileGroupId) {
        var optionalGroup = fileGroupRepository.findById(fileGroupId);

        if (optionalGroup.isEmpty()) {
            return;
        }

        fileAclService.requireModify(optionalGroup.get()
            .getFiles());
    }

    /**
     * Publishing a single file sends it to the admin backend, so the caller must hold the modify privilege on it. Runs synchronously in the caller's request so
     * that the request itself fails instead of the background task.
     *
     * @throws ResponseStatusException 404 when the file does not exist
     * @throws AccessDeniedException   when the file is not modifiable by the current user
     */
    @Transactional(readOnly = true)
    public void authorizeFilePublish(long fileId) {
        var file = fileRepository.findById(fileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File " + fileId + " not found"));
        fileAclService.requireModify(file);
    }

    @Transactional(readOnly = true)
    public void authorizeFilesPublish(List<Long> fileIds) {
        var requestedIds = new LinkedHashSet<>(fileIds);
        var files = fileRepository.findAllById(requestedIds);
        var foundIds = files.stream()
            .map(FileEntity::getId)
            .collect(java.util.stream.Collectors.toSet());
        var missingIds = requestedIds.stream()
            .filter(fileId -> !foundIds.contains(fileId))
            .toList();
        if (!missingIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Files not found: " + missingIds);
        }

        fileAclService.requireModifyBulk(files);
    }

    /**
     * Starts a background task of one step that publishes a single file as a site file. No gallery is created or linked on the admin side: the ingest request
     * carries neither a gallery ID nor a gallery name.
     */
    @Transactional(readOnly = true)
    public TaskProgress publishFile(PublishFileRequest request) {
        var title = "Publish file "
                + fileRepository.findById(request.getFileId())
                    .map(FileEntity::getFilename)
                    .orElse("#" + request.getFileId());
        var proxy = applicationContext.getBean(PublishService.class);
        return taskRunner.submitDurable(TaskTypeEnum.PUBLISH_FILE.name(), title, 1, request, progress -> {
            proxy.publishFileNow(request, progress);
            return null;
        });
    }

    @Transactional(readOnly = true)
    public TaskProgress publishFiles(PublishFilesRequest request) {
        var proxy = applicationContext.getBean(PublishService.class);
        return taskRunner.submitDurable(TaskTypeEnum.PUBLISH_FILES.name(), "Publish selected files", request.getFileIds()
            .size(), request, progress -> {
                proxy.publishFilesNow(request, progress);
                return null;
            });
    }

    @Transactional
    public void publishFilesNow(PublishFilesRequest request, TaskProgress progress) {
        for (var fileId : request.getFileIds()) {
            progress.checkpoint();
            try {
                publishFileNow(PublishFileRequest.builder()
                    .fileId(fileId)
                    .build(), progress);
            } catch (fi.poltsi.vempain.common.task.TaskCancelledException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to publish file {}", fileId, e);
                progress.advanceFailed("Failed file " + fileId + ": " + e.getMessage());
            }
        }
    }

    /**
     * Publishes one file in the calling thread. The upload is registered as a compensation of the task when it created a new site file, so that cancelling (or
     * a later failure) removes it from the admin backend again.
     *
     * @throws IllegalStateException when the file or its export does not exist
     */
    @Transactional
    public void publishFileNow(PublishFileRequest request, TaskProgress progress) {
        var fileEntity = fileRepository.findById(request.getFileId())
            .orElseThrow(() -> new IllegalStateException("File " + request.getFileId() + " not found"));
        var exportFilePath = resolveExportedPath(fileEntity.getId());

        if (exportFilePath == null) {
            fileMetrics.published(PublishResult.SKIPPED_NO_EXPORT);
            throw new IllegalStateException("No export file for " + fileEntity.getFilename());
        }
        if (!Files.exists(exportFilePath)) {
            fileMetrics.published(PublishResult.MISSING_ON_DISK);
            throw new IllegalStateException("No export file for " + fileEntity.getFilename());
        }

        if (progress != null) {
            progress.checkpoint();
        }

        try {
            var response = uploadFile(fileEntity, exportFilePath, FileIngestRequest.builder()
                .sortOrder(0)
                .acls(toIngestAcls(request.getAcls())), "vempain-");
            if (progress != null) {
                registerUploadCompensation(progress, fileEntity.getFilename(), response);
                progress.advance("Published " + fileEntity.getFilename());
            }
            log.debug("Published file {} as site file {}", fileEntity.getFilename(), response == null ? null : response.getSiteFileId());
        } catch (IOException e) {
            fileMetrics.published(PublishResult.FAILED);
            throw new IllegalStateException("Failed to publish " + fileEntity.getFilename() + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            fileMetrics.published(PublishResult.FAILED);
            throw e;
        }
    }

    /**
     * Starts a background task that publishes one file group. One step per exported file, because only the exported files are transferred; the total is known
     * from the start so that the progress is right from the first poll (a group without exported files is one step that reports it has nothing to publish).
     */
    public TaskProgress publishFileGroup(PublishFileGroupRequest request) {
        var proxy = applicationContext.getBean(PublishService.class);
        return taskRunner.submitDurable(TaskTypeEnum.PUBLISH_FILE_GROUP.name(), "Publish file group " + groupTitle(request),
                groupSteps(request.getFileGroupId()), request, progress -> {
                    proxy.publishFileGroupNow(request, progress, true);
                    return null;
                });
    }

    /**
     * Steps a file group contributes to a task: one per exported file, and one for a group without exported files so that it is still reported.
     */
    private long groupSteps(long fileGroupId) {
        return Math.max(1L, countExportedFilesInGroup(fileGroupId));
    }

    /**
     * Publishes one file group in the calling thread. Only the files of the group that have an exported file are transferred, so they alone make up the steps
     * of the task; a file without an export is not published and is not counted.
     *
     * @param progress        task to check for cancellation and to register undo actions with; null when not running as a task
     * @param reportFileSteps whether every exported file is reported as a step of {@code progress} (the publish-all task counts groups instead)
     * @throws IllegalStateException when the group does not exist
     */
    @Transactional
    public void publishFileGroupNow(PublishFileGroupRequest request, TaskProgress progress, boolean reportFileSteps) {
        var fileGroup = fileGroupRepository.findById(request.getFileGroupId())
            .orElseThrow(() -> new IllegalStateException("File group " + request.getFileGroupId() + " not found"));

        if (fileGroup.getFiles() == null
            || fileGroup.getFiles()
                .isEmpty()) {
            log.debug("File group {} has no files to publish", request.getFileGroupId());
            if (progress != null
                && reportFileSteps) {
                progress.advance("File group has no files to publish");
            }
            return;
        }

        var exportedFiles = collectExportedFiles(fileGroup);

        if (exportedFiles.isEmpty()) {
            log.debug("File group {} has no exported files to publish", request.getFileGroupId());
            if (progress != null
                && reportFileSteps) {
                progress.advance("File group has no exported files to publish");
            }
            return;
        }

        if (progress != null
            && reportFileSteps) {
            progress.setTotalSteps(exportedFiles.size());
        }

        var galleryId = publishFiles(fileGroup, exportedFiles, request, progress, reportFileSteps);

        if (galleryId != null) {
            var previousGalleryId = fileGroup.getGalleryId();
            if (!galleryId.equals(previousGalleryId)
                && progress != null) {
                var proxy = applicationContext.getBean(PublishService.class);
                var groupId = fileGroup.getId();
                progress.registerCompensation("Restore gallery link of file group " + groupTitle(request),
                        () -> proxy.restoreGalleryId(groupId, previousGalleryId));
            }
            fileGroup.setGalleryId(galleryId);
            fileGroupRepository.save(fileGroup);
            log.debug("File group {} published to gallery ID {}", request.getFileGroupId(), galleryId);
        } else {
            log.warn("No files were published for group {}", request.getFileGroupId());
        }
    }

    /**
     * Undo of the gallery link update made by {@link #publishFileGroupNow}.
     */
    @Transactional
    public void restoreGalleryId(long fileGroupId, Long previousGalleryId) {
        fileGroupRepository.findById(fileGroupId)
            .ifPresent(group -> {
                group.setGalleryId(previousGalleryId);
                fileGroupRepository.save(group);
            });
    }

    /**
     * A file of a group together with the path of its exported file.
     */
    private record ExportedFile(FileEntity file, Path exportPath) {}

    /**
     * The files of the group that have an exported file, in the order of the group. Files without an export are left out: they are never transferred, so they
     * are no steps of the publish.
     */
    private List<ExportedFile> collectExportedFiles(FileGroupEntity fileGroup) {
        var exportedFiles = new ArrayList<ExportedFile>();

        for (var fileEntity : fileGroup.getFiles()) {
            var exportFilePath = resolveExportedPath(fileEntity.getId());

            if (exportFilePath == null) {
                log.debug("File {} of group {} has no export file, not publishing it", fileEntity.getFilename(), fileGroup.getId());
                fileMetrics.published(PublishResult.SKIPPED_NO_EXPORT);
                continue;
            }

            exportedFiles.add(new ExportedFile(fileEntity, exportFilePath));
        }

        return exportedFiles;
    }

    private Long publishFiles(FileGroupEntity fileGroup, List<ExportedFile> exportedFiles, PublishFileGroupRequest request, TaskProgress progress,
            boolean reportFileSteps) {
        Long galleryId = null;
        // The order of the file group files should be by file name ascending so we use a simple counter here
        long sortOrder = 0L;

        for (var exportedFile : exportedFiles) {
            if (progress != null) {
                // Stop here when the user cancelled; nothing of this file has been sent yet
                progress.checkpoint();
            }
            var fileEntity = exportedFile.file();
            var exportFilePath = exportedFile.exportPath();

            if (!Files.exists(exportFilePath)) {
                // The export is registered but missing on disk: the file was counted as a step, so it is reported as a failed one
                log.warn("Export file of {} does not exist on disk, skipping: {}", fileEntity.getFilename(), exportFilePath);
                fileMetrics.published(PublishResult.MISSING_ON_DISK);
                reportFailure(reportFileSteps ? progress : null, "Export file missing on disk for " + fileEntity.getFilename());
                continue;
            }

            try {
                var fileIngestResponse = uploadFile(fileEntity, exportFilePath, buildIngestRequestBase(fileEntity, request, fileGroup, sortOrder), "vempain-");
                sortOrder++;
                galleryId = fileIngestResponse == null ? galleryId : fileIngestResponse.getGalleryId();
                log.debug("Published file {} from group {} as site file to gallery ID {}", fileEntity.getFilename(), request.getFileGroupId(), galleryId);
                if (progress != null) {
                    registerUploadCompensation(progress, fileEntity.getFilename(), fileIngestResponse);
                    if (reportFileSteps) {
                        progress.advance("Published " + fileEntity.getFilename());
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to publish file {} from group {}", fileEntity.getFilename(), request.getFileGroupId(), ex);
                fileMetrics.published(PublishResult.FAILED);
                reportFailure(reportFileSteps ? progress : null, "Failed to publish " + fileEntity.getFilename() + ": " + ex.getMessage());
            }
        }

        return galleryId;
    }

    /**
     * A site file that the upload created is removed again when the task is cancelled. An upload that replaced an existing site file cannot be reverted,
     * because the previous content is gone.
     */
    private void registerUploadCompensation(TaskProgress progress, String filename, fi.poltsi.vempain.admin.api.response.file.FileIngestResponse response) {
        if (response == null
            || response.getSiteFileId() == null) {
            return;
        }
        if (response.isUpdated()) {
            log.debug("Site file {} replaced an existing file; the upload cannot be reverted", response.getSiteFileId());
            return;
        }
        var siteFileId = response.getSiteFileId();
        progress.registerCompensation("Remove " + filename + " from the admin backend", () -> vempainAdminService.deleteSiteFile(siteFileId));
    }

    private static void reportFailure(TaskProgress progress, String message) {
        if (progress != null) {
            progress.advanceFailed(message);
        }
    }

    private FileIngestRequest.FileIngestRequestBuilder buildIngestRequestBase(FileEntity fileEntity, PublishFileGroupRequest request, FileGroupEntity fileGroup,
            long sortOrder) {
        return FileIngestRequest.builder()
            .sortOrder(sortOrder)
            .galleryId(fileGroup.getGalleryId())
            .galleryName(request.getGalleryName())
            .galleryDescription(request.getGalleryDescription())
            .acls(toIngestAcls(request.getAcls()));
    }

    /**
     * Resizes images, fills the ingest request from the file entity, and uploads the file to the admin backend.
     */
    private fi.poltsi.vempain.admin.api.response.file.FileIngestResponse uploadFile(FileEntity fileEntity, Path exportFilePath,
            FileIngestRequest.FileIngestRequestBuilder builder, String tempPrefix) throws IOException {
        var metadataEntries = metadataService.getFileMetadata(fileEntity.getId());
        var metadataJson = collectStandardMetadataAsJson(metadataEntries, fileEntity);

        if (metadataJson == null
            || metadataJson.isBlank()) {
            metadataJson = "[]";
        }

        var siteFileName = fileEntity.getFilename();
        var uploadPath = exportFilePath;
        Path tempPathToDelete = null;

        try {
            Dimension imageVideoDimensions = null;
            if (fileEntity.getFileType()
                .equals(FileTypeEnum.IMAGE)) {
                // Create temp file with same extension in system temp dir and resize: smaller dimension to siteImageSize, quality 0.7
                Path tempFile = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), tempPrefix, "." + exportFileType);
                imageVideoDimensions = imageTool.resizeImage(exportFilePath, tempFile, siteImageSize, 0.7f, metadataJson);
                tempPathToDelete = tempFile;
                uploadPath = tempFile;

                int suffixIndex = siteFileName.lastIndexOf('.');
                if (suffixIndex > 0) {
                    siteFileName = siteFileName.substring(0, suffixIndex) + "." + exportFileType;
                }
            }

            // We need to send the mimetype of the uploaded file, not the original which may have a different type
            var exportFileJsonObject = MetadataTool.extractMetadataJsonObject(uploadPath.toFile());
            var mimetype = MetadataTool.extractMimetype(exportFileJsonObject);
            var copyrightResponse = CopyrightResponse.builder()
                .creatorName(fileEntity.getCreatorName())
                .creatorEmail(fileEntity.getCreatorEmail())
                .creatorCountry(fileEntity.getCreatorCountry())
                .creatorUrl(fileEntity.getCreatorUrl())
                .rightsHolder(fileEntity.getRightsHolder())
                .rightsTerms(fileEntity.getRightsTerms())
                .rightsUrl(fileEntity.getRightsUrl())
                .build();
            LocationResponse locationResponse = null;
            if (fileEntity.getGpsLocation() != null) {
                // Add location only if the location is outside guarded areas
                if (!locationService.isGuardedLocation(fileEntity.getGpsLocation())) {
                    locationResponse = fileEntity.getGpsLocation()
                        .toResponse();
                    log.debug("File {} location is outside guarded areas, adding location data", fileEntity.getFilename());
                } else {
                    log.debug("File {} location is inside guarded areas, not publishing location data", fileEntity.getFilename());
                }
            }

            var fileIngestRequest = builder.fileId(fileEntity.getId())
                .fileName(siteFileName)
                .filePath(normalizeIngestPath(fileEntity.getFilePath(), fileEntity.getFileType()))
                .mimeType(mimetype)
                .comment(fileEntity.getDescription() != null ? fileEntity.getDescription() : "")
                .metadata(metadataJson)
                .sha256sum(computeSha256(uploadPath.toFile()))
                .originalDateTime(fileEntity.getOriginalDatetime())
                .tags(tagService.getTagRequestsByFileId(fileEntity.getId()))
                .location(locationResponse)
                .copyright(copyrightResponse)
                .build();

            if (imageVideoDimensions != null) {
                fileIngestRequest.setWidth(imageVideoDimensions.width);
                fileIngestRequest.setHeight(imageVideoDimensions.height);
            }

            if (fileEntity.getFileType()
                .equals(FileTypeEnum.VIDEO)) {
                fileIngestRequest.setLength(((VideoFileEntity) fileEntity).getDuration());
            } else if (fileEntity.getFileType()
                .equals(FileTypeEnum.AUDIO)) {
                fileIngestRequest.setLength(((AudioFileEntity) fileEntity).getDuration());
            } else if (fileEntity.getFileType()
                .equals(FileTypeEnum.DOCUMENT)) {
                fileIngestRequest.setPages(((DocumentFileEntity) fileEntity).getPageCount());
            }

            // The admin backend authenticates the call with the configured API token; a refusal is final (no login to retry)
            var uploadSize = Files.size(uploadPath);
            var response = vempainAdminService.uploadAsSiteFile(uploadPath.toFile(), fileIngestRequest);
            fileMetrics.published(response != null
                                  && response.isUpdated() ? PublishResult.UPDATED : PublishResult.UPLOADED);
            fileMetrics.publishedBytes(uploadSize);
            return response;
        } finally {
            if (tempPathToDelete != null) {
                try {
                    Files.deleteIfExists(tempPathToDelete);
                } catch (IOException ioe) {
                    log.warn("Failed to delete temp file {}", tempPathToDelete, ioe);
                }
            }
        }
    }

    /**
     * Number of files in a group that have an exported file, without loading the collection. A publish transfers exactly these files, so this is the count the
     * publish tasks are sized with.
     */
    public long countExportedFilesInGroup(long fileGroupId) {
        return fileGroupRepository.countExportedFilesById(fileGroupId);
    }

    public boolean fileGroupExists(long fileGroupId) {
        return fileGroupRepository.existsById(fileGroupId);
    }

    @Transactional
    public boolean republishSiteFile(FileEntity fileEntity) {
        if (fileEntity == null) {
            return false;
        }

        var exportFilePath = resolveExportedPath(fileEntity.getId());
        if (exportFilePath == null
            || !Files.exists(exportFilePath)) {
            log.debug("No exported file found on disk for file id {}", fileEntity.getId());
            return false;
        }

        try {
            uploadFile(fileEntity, exportFilePath, FileIngestRequest.builder()
                .sortOrder(0), "vempain-refresh-");
            return true;
        } catch (Exception e) {
            log.warn("Failed to republish site file for file id {}", fileEntity.getId(), e);
            return false;
        }
    }

    @Transactional(readOnly = true)
    public TaskProgress publishAllFileGroups() {
        return publishAllFileGroups(null);
    }

    /**
     * Starts one background task that publishes every file group the caller may fully modify, one step per exported file (a group without exported files counts
     * as one step). Groups with files the caller cannot modify are skipped. The ACL entries of the request, if any, are granted on every published group.
     * Returns the task; {@code total_steps} is the number of files it will transfer.
     */
    // Read-only transaction so that the lazily loaded file collections can be checked against the caller's ACL privileges
    @Transactional(readOnly = true)
    public TaskProgress publishAllFileGroups(PublishAllFileGroupsRequest publishAllRequest) {
        var groups = collectPublishableGroups(publishAllRequest == null ? null : publishAllRequest.getAcls());
        var requests = groups.requests();
        var payload = java.util.Map.of("requests", requests);
        var proxy = applicationContext.getBean(PublishService.class);
        return taskRunner.submitDurable(TaskTypeEnum.PUBLISH_ALL_FILE_GROUPS.name(), "Publish all file groups", groups.totalSteps(), payload, progress -> {
            publishGroupsAsSteps(requests, progress, proxy);
            return null;
        });
    }

    /**
     * Durable worker body for publishing all groups.
     */
    @Transactional
    public void publishAllFileGroupsNow(TaskProgress progress) {
        var mapper = new tools.jackson.databind.ObjectMapper();
        var payload = mapper.readTree(progress.getPayload());
        var requests = new ArrayList<PublishFileGroupRequest>();
        for (var node : payload.path("requests")) {
            requests.add(mapper.treeToValue(node, PublishFileGroupRequest.class));
        }
        publishGroupsAsSteps(requests, progress, this);
    }

    /**
     * Publishes the groups one after the other, every exported file as one step of the task. A group that fails as a whole marks the steps of its remaining
     * files as failed, so that the task still ends at its total.
     */
    private void publishGroupsAsSteps(List<PublishFileGroupRequest> requests, TaskProgress progress, PublishService target) {
        for (var request : requests) {
            progress.checkpoint();
            var expectedSteps = groupSteps(request.getFileGroupId());
            var stepsBefore = progress.getCompletedSteps()
                .get();
            try {
                target.publishFileGroupNow(request, progress, true);
                progress.message("Published " + groupTitle(request));
            } catch (fi.poltsi.vempain.common.task.TaskCancelledException e) {
                throw e;
            } catch (Exception e) {
                log.error("Publish group {} failed", request.getFileGroupId(), e);
                var remaining = expectedSteps - (progress.getCompletedSteps()
                    .get() - stepsBefore);
                var message = "Failed " + groupTitle(request) + ": " + e.getMessage();
                for (long step = 0; step < Math.max(1L, remaining); step++) {
                    progress.advanceFailed(message);
                }
            }
        }
    }

    /**
     * The groups the caller may publish and the number of task steps they add up to (one per exported file, one for a group without exported files).
     */
    private record PublishableGroups(List<PublishFileGroupRequest> requests, long totalSteps) {}

    private PublishableGroups collectPublishableGroups(List<PublishAclRequest> acls) {
        var requests = new ArrayList<PublishFileGroupRequest>();
        long totalSteps = 0;
        int page = 0;
        int size = 50;

        while (true) {
            var pageable = PageRequest.of(page, size, Sort.by("path"));
            var pg = fileGroupRepository.searchFileGroups(null, false, pageable);
            if (!pg.hasContent()) {
                break;
            }

            for (var projection : pg.getContent()) {
                var groupId = projection.id();
                var group = fileGroupRepository.findById(groupId);

                if (group.isEmpty()
                    || !fileAclService.canModifyAll(group.get()
                        .getFiles())) {
                    log.warn("Skipping publish for file group {} because the user lacks modify permission on all of its files", groupId);
                    continue;
                }

                requests.add(PublishFileGroupRequest.builder()
                    .fileGroupId(groupId)
                    .galleryName(projection.groupName())
                    .galleryDescription(projection.description() != null
                                        && projection.description()
                                            .length() > 2 ? projection.description() : projection.groupName())
                    .acls(acls == null
                          || acls.isEmpty() ? null : acls)
                    .build());
                totalSteps += Math.max(1L, projection.exportedFileCount());
            }

            page++;
            if (page >= pg.getTotalPages()) {
                break;
            }
        }

        return new PublishableGroups(requests, totalSteps);
    }

    private static String groupTitle(PublishFileGroupRequest request) {
        return request.getGalleryName() != null
               && !request.getGalleryName()
                   .isBlank() ? request.getGalleryName() : "#" + request.getFileGroupId();
    }

    private Path resolveExportedPath(long fileId) {
        var optionalExportFileEntity = exportFileRepository.findByFileId(fileId);

        if (optionalExportFileEntity.isEmpty()) {
            log.debug("No exported file found for file entity with ID {}", fileId);
            return null;
        }

        var exportFileEntity = optionalExportFileEntity.get();

        String relativePath = exportFileEntity.getFilePath() == null ? "" : exportFileEntity.getFilePath();

        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        return Path.of(exportRootDirectory)
            .resolve(relativePath)
            .resolve(exportFileEntity.getFilename());
    }

    private String normalizeIngestPath(String filePath, FileTypeEnum fileType) {
        if (filePath == null) {
            return "";
        }

        var relativePath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        // Then remove the file type prefix if present
        var prefix = fileType.name()
            .toLowerCase()
                + "/";
        if (relativePath.startsWith(prefix)) {
            relativePath = relativePath.substring(prefix.length());
        }

        return relativePath;
    }
}
