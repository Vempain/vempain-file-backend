package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.api.TaskTypeEnum;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.response.CopyrightResponse;
import fi.poltsi.vempain.file.api.response.LocationResponse;
import fi.poltsi.vempain.file.entity.AudioFileEntity;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.feign.VempainAdminTokenProvider;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.task.TaskProgress;
import fi.poltsi.vempain.file.task.TaskRunner;
import fi.poltsi.vempain.file.tools.ImageTool;
import fi.poltsi.vempain.file.tools.MetadataTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;
import static fi.poltsi.vempain.file.tools.MetadataTool.collectStandardMetadataAsJson;

/**
 * Publishes file groups to the admin backend. Publishing runs as background tasks ({@link TaskRunner}); the caller gets the task
 * id back immediately and follows the progress through the task API.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PublishService {
	private final FileGroupRepository  fileGroupRepository;
	private final ExportFileRepository exportFileRepository;
	private final MetadataRepository metadataRepository;

	private final VempainAdminService vempainAdminService;
	private final TagService          tagService;
	private final LocationService     locationService;
	private final FileAclService fileAclService;

	private final VempainAdminTokenProvider vempainAdminTokenProvider;
	private final ImageTool          imageTool;
	private final ApplicationContext applicationContext;
	private final TaskRunner         taskRunner;

	@Value("${vempain.site-image-size:1200}")
	private int siteImageSize;

	@Value("${vempain.export-root-directory}")
	private String exportRootDirectory;

	@Value("${vempain.export-file-type}")
	private String exportFileType;

	/**
	 * Publishing a file group sends every file of the group to the admin backend, so the caller must hold the modify privilege on
	 * every file in the group. This runs synchronously in the caller's request so that a denial fails the request itself instead of
	 * the background task.
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
	 * Starts a background task that publishes one file group. One step per file.
	 */
	public TaskProgress publishFileGroup(PublishFileGroupRequest request) {
		var proxy = applicationContext.getBean(PublishService.class);
		return taskRunner.submitDurable(TaskTypeEnum.PUBLISH_FILE_GROUP.name(), "Publish file group " + groupTitle(request), 0, request,
										progress -> {
											proxy.publishFileGroupNow(request, progress, true);
											return null;
										});
	}

	/**
	 * Publishes one file group in the calling thread.
	 *
	 * @param progress        task to check for cancellation and to register undo actions with; null when not running as a task
	 * @param reportFileSteps whether every file is reported as a step of {@code progress} (the publish-all task counts groups instead)
	 * @throws IllegalStateException when the group does not exist
	 */
	@Transactional
	public void publishFileGroupNow(PublishFileGroupRequest request, TaskProgress progress, boolean reportFileSteps) {
		var fileGroup = fileGroupRepository.findById(request.getFileGroupId())
										   .orElseThrow(() -> new IllegalStateException("File group " + request.getFileGroupId() + " not found"));

		if (fileGroup.getFiles() == null || fileGroup.getFiles()
													 .isEmpty()) {
			log.debug("File group {} has no files to publish", request.getFileGroupId());
			if (progress != null && reportFileSteps) {
				progress.message("File group has no files to publish");
			}
			return;
		}

		if (progress != null && reportFileSteps) {
			progress.setTotalSteps(fileGroup.getFiles()
											.size());
		}

		var galleryId = publishFiles(fileGroup, request, progress, reportFileSteps);

		if (galleryId != null) {
			var previousGalleryId = fileGroup.getGalleryId();
			if (!galleryId.equals(previousGalleryId) && progress != null) {
				var proxy   = applicationContext.getBean(PublishService.class);
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

	private Long publishFiles(FileGroupEntity fileGroup, PublishFileGroupRequest request, TaskProgress progress, boolean reportFileSteps) {
		Long galleryId = null;
		// The order of the file group files should be by file name ascending so we use a simple counter here
		long sortOrder = 0L;

		for (var fileEntity : fileGroup.getFiles()) {
			if (progress != null) {
				// Stop here when the user cancelled; nothing of this file has been sent yet
				progress.checkpoint();
			}
			var exportFilePath = resolveExportedPath(fileEntity.getId());

			if (exportFilePath == null || !Files.exists(exportFilePath)) {
				log.debug("Export file does not exist, skipping: {}", exportFilePath);
				reportFailure(reportFileSteps ? progress : null, "No export file for " + fileEntity.getFilename());
				continue;
			}

			try {
				var fileIngestResponse = uploadFile(fileEntity, exportFilePath, buildIngestRequestBase(fileEntity, request, fileGroup, sortOrder),
													"vempain-");
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
				reportFailure(reportFileSteps ? progress : null, "Failed to publish " + fileEntity.getFilename() + ": " + ex.getMessage());
			}
		}

		return galleryId;
	}

	/**
	 * A site file that the upload created is removed again when the task is cancelled. An upload that replaced an existing
	 * site file cannot be reverted, because the previous content is gone.
	 */
	private void registerUploadCompensation(TaskProgress progress, String filename,
											fi.poltsi.vempain.admin.api.response.file.FileIngestResponse response) {
		if (response == null || response.getSiteFileId() == null) {
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

	private FileIngestRequest.FileIngestRequestBuilder buildIngestRequestBase(FileEntity fileEntity, PublishFileGroupRequest request,
																			  FileGroupEntity fileGroup, long sortOrder) {
		return FileIngestRequest.builder()
								.sortOrder(sortOrder)
								.galleryId(fileGroup.getGalleryId())
								.galleryName(request.getGalleryName())
								.galleryDescription(request.getGalleryDescription());
	}

	/**
	 * Resizes images, fills the ingest request from the file entity and uploads the file to the admin backend, retrying once the
	 * admin token has been renewed after an authentication failure.
	 */
	private fi.poltsi.vempain.admin.api.response.file.FileIngestResponse uploadFile(FileEntity fileEntity, Path exportFilePath,
																					FileIngestRequest.FileIngestRequestBuilder builder,
																					String tempPrefix) throws IOException {
		var  metadataList     = metadataRepository.findByFile(fileEntity);
		var  metadataJson     = collectStandardMetadataAsJson(metadataList, fileEntity);
		var  siteFileName     = fileEntity.getFilename();
		Path uploadPath       = exportFilePath;
		Path tempPathToDelete = null;

		try {
			Dimension imageVideoDimensions = null;
			if (fileEntity.getFileType()
						  .equals(FileTypeEnum.IMAGE)) {
				// Create temp file with same extension in system temp dir and resize: smaller dimension to siteImageSize, quality 0.7
				Path tempFile = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), tempPrefix, "." + exportFileType);
				imageVideoDimensions = imageTool.resizeImage(exportFilePath, tempFile, siteImageSize, 0.7f, metadataJson);
				tempPathToDelete     = tempFile;
				uploadPath           = tempFile;

				int suffixIndex = siteFileName.lastIndexOf('.');
				if (suffixIndex > 0) {
					siteFileName = siteFileName.substring(0, suffixIndex) + "." + exportFileType;
				}
			}

			// We need to send the mimetype of the uploaded file, not the original which may have a different type
			var exportFileJsonObject = MetadataTool.extractMetadataJsonObject(uploadPath.toFile());
			var mimetype             = MetadataTool.extractMimetype(exportFileJsonObject);
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

			var fileIngestRequest = builder.fileName(siteFileName)
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

			// Upload with authentication retry
			final int maxRetries = 3;
			int       attempt    = 0;

			while (true) {
				try {
					return vempainAdminService.uploadAsSiteFile(uploadPath.toFile(), fileIngestRequest);
				} catch (VempainAuthenticationException authEx) {
					attempt++;
					if (attempt >= maxRetries) {
						log.error("Authentication failed after {} attempts for file {}", attempt, fileEntity.getFilename());
						throw authEx;
					}
					log.warn("Authentication failed (attempt {}/{}). Re-authenticating and retrying...", attempt, maxRetries);
					vempainAdminTokenProvider.login();
				}
			}
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
	 * Count files in a group without loading the full collection.
	 */
	public long countFilesInGroup(long fileGroupId) {
		return fileGroupRepository.countById(fileGroupId);
	}

	@Transactional
	public boolean republishSiteFile(FileEntity fileEntity) {
		if (fileEntity == null) {
			return false;
		}

		var exportFilePath = resolveExportedPath(fileEntity.getId());
		if (exportFilePath == null || !Files.exists(exportFilePath)) {
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

	/**
	 * Starts one background task that publishes every file group the caller may fully modify, one step per group. Groups with
	 * files the caller cannot modify are skipped. Returns the task; {@code total_steps} is the number of groups it will publish.
	 */
	// Read-only transaction so that the lazily loaded file collections can be checked against the caller's ACL privileges
	@Transactional(readOnly = true)
	public TaskProgress publishAllFileGroups() {
		var requests = collectPublishableGroups();
		var payload = java.util.Map.of("requests", requests);
		var proxy   = applicationContext.getBean(PublishService.class);
		return taskRunner.submitDurable(TaskTypeEnum.PUBLISH_ALL_FILE_GROUPS.name(), "Publish all file groups", requests.size(), payload,
										progress -> {
											for (var request : requests) {
												progress.checkpoint();
												try {
													proxy.publishFileGroupNow(request, progress, false);
													progress.advance("Published " + groupTitle(request));
												} catch (fi.poltsi.vempain.file.task.TaskCancelledException e) {
													throw e;
												} catch (Exception e) {
													log.error("Publish group {} failed", request.getFileGroupId(), e);
													progress.advanceFailed("Failed " + groupTitle(request) + ": " + e.getMessage());
												}
											}
											return null;
										});
	}

	/**
	 * Durable worker body for publishing all groups.
	 */
	@Transactional
	public void publishAllFileGroupsNow(TaskProgress progress) {
		var payload = new tools.jackson.databind.ObjectMapper().readTree(progress.getPayload());
		for (var node : payload.path("requests")) {
			progress.checkpoint();
			var request = new tools.jackson.databind.ObjectMapper().treeToValue(node, PublishFileGroupRequest.class);
			try {
				publishFileGroupNow(request, progress, false);
				progress.advance("Published " + groupTitle(request));
			} catch (fi.poltsi.vempain.file.task.TaskCancelledException e) {
				throw e;
			} catch (Exception e) {
				log.error("Publish group {} failed", request.getFileGroupId(), e);
				progress.advanceFailed("Failed " + groupTitle(request) + ": " + e.getMessage());
			}
		}
	}

	private List<PublishFileGroupRequest> collectPublishableGroups() {
		var requests = new ArrayList<PublishFileGroupRequest>();
		int page     = 0;
		int size     = 50;

		while (true) {
			var pageable = PageRequest.of(page, size, Sort.by("path"));
			var pg       = fileGroupRepository.searchFileGroups(null, false, pageable);
			if (!pg.hasContent()) {
				break;
			}

			for (var projection : pg.getContent()) {
				var groupId = projection.id();
				var group   = fileGroupRepository.findById(groupId);

				if (group.isEmpty() || !fileAclService.canModifyAll(group.get()
																		 .getFiles())) {
					log.warn("Skipping publish for file group {} because the user lacks modify permission on all of its files", groupId);
					continue;
				}

				requests.add(PublishFileGroupRequest.builder()
													.fileGroupId(groupId)
													.galleryName(projection.groupName())
													.galleryDescription(projection.description() != null && projection.description()
																													  .length() > 2 ?
																		projection.description() : projection.groupName())
													.build());
			}

			page++;
			if (page >= pg.getTotalPages()) {
				break;
			}
		}

		return requests;
	}

	private static String groupTitle(PublishFileGroupRequest request) {
		return request.getGalleryName() != null && !request.getGalleryName()
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
		                     .toLowerCase() + "/";
		if (relativePath.startsWith(prefix)) {
			relativePath = relativePath.substring(prefix.length());
		}

		return relativePath;
	}
}
