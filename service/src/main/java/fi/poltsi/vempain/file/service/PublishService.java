package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.auth.tools.JsonTools;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.response.CopyrightResponse;
import fi.poltsi.vempain.file.api.response.LocationResponse;
import fi.poltsi.vempain.file.entity.AudioFileEntity;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.feign.VempainAdminTokenProvider;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
import fi.poltsi.vempain.file.tools.MetadataTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;
import static fi.poltsi.vempain.file.tools.MetadataTool.collectStandardMetadataAsJson;


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

	private final VempainAdminTokenProvider vempainAdminTokenProvider;
	private final ImageTool            imageTool;
	private final ApplicationContext   applicationContext;
	private final PublishProgressStore progressStore;

	@Value("${vempain.site-image-size:1200}")
	private int siteImageSize;

	@Value("${vempain.export-root-directory}")
	private String exportRootDirectory;

	@Value("${vempain.export-file-type}")
	private String exportFileType;

	@Async
	@Transactional
	public void publishFileGroup(PublishFileGroupRequest publishFileGroupRequest) {
		// mark started in progress store (if running under proxy we still mark here)
		progressStore.markStarted(publishFileGroupRequest.getFileGroupId());

		try {
			// Load group and files
			var optionalGroup = fileGroupRepository.findById(publishFileGroupRequest.getFileGroupId());
			if (optionalGroup.isEmpty()) {
				log.warn("File group {} not found", publishFileGroupRequest.getFileGroupId());
				progressStore.markFailed(publishFileGroupRequest.getFileGroupId());
				return;
			}

			var fileGroup = optionalGroup.get();

			if (fileGroup.getFiles() == null
				|| fileGroup.getFiles()
							.isEmpty()) {
				log.info("File group {} has no files to publish", publishFileGroupRequest.getFileGroupId());
				progressStore.markCompleted(publishFileGroupRequest.getFileGroupId());
				return;
			}
			Long galleryId = null;
			// The order of the file group files should be by file name ascending so we use a simple counter here
			long sortOrder = 0L;

			for (var fileEntity : fileGroup.getFiles()) {
				var metadataList = metadataRepository.findByFile(fileEntity);
				var metadataJson = collectStandardMetadataAsJson(metadataList, fileEntity);

				var exportFilePath = resolveExportedPath(fileEntity.getId());
				var siteFileName   = fileEntity.getFilename();

				if (exportFilePath == null
					|| !Files.exists(exportFilePath)) {
					log.warn("Export file does not exist, skipping: {}", exportFilePath);
					continue;
				}

				Path uploadPath       = exportFilePath;
				Path tempPathToDelete = null;

				// Fetch the tags belonging to the file
				var tagRequests = tagService.getTagRequestsByFileId(fileEntity.getId());

				try {
					Dimension imageVideoDimensions = null;
					if (fileEntity.getFileType()
								  .equals(FileTypeEnum.IMAGE)) {
						// Create temp file with same extension in system temp dir
						Path tempFile = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "vempain-", "." + exportFileType);
						// Resize: smaller dimension to siteImageSize, keep quality 0.7
						imageVideoDimensions = imageTool.resizeImage(exportFilePath, tempFile, siteImageSize, 0.7f, metadataJson);
						tempPathToDelete = tempFile;
						exportFilePath   = tempFile;
						uploadPath       = tempFile;

						// We need to also update the siteFileName to replace the original extension with
						int suffixIndex = siteFileName.lastIndexOf('.');
						if (suffixIndex > 0) {
							siteFileName = siteFileName.substring(0, suffixIndex) + "." + exportFileType;
						}
					}

					// Build ingest request, we need to send the mimetype of the temp file, not the original which may have a different type
					var exportFileJsonObject = MetadataTool.extractMetadataJsonObject(exportFilePath.toFile());
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
					// Use relation from FileEntity instead of repository lookup
					if (fileEntity.getGpsLocation() != null) {
						// Add location only if the location is outside guarded areas
						if (!locationService.isGuardedLocation(fileEntity.getGpsLocation())) {
							locationResponse = fileEntity.getGpsLocation()
														 .toResponse();
							log.info("File {} location is outside guarded areas, adding location data", fileEntity.getFilename());
						} else {
							log.info("File {} location is inside guarded areas, not publishing location data", fileEntity.getFilename());
						}
					}

					var fileIngestRequest = FileIngestRequest.builder()
															 .fileName(siteFileName)
															 .sortOrder(sortOrder)
															 .filePath(normalizeIngestPath(fileEntity.getFilePath()))
															 .mimeType(mimetype)
															 .comment(fileEntity.getDescription() != null ? fileEntity.getDescription() : "")
															 .metadata(metadataJson)
															 .sha256sum(computeSha256(uploadPath.toFile()))
															 .originalDateTime(fileEntity.getOriginalDatetime())
															 .galleryId(fileGroup.getGalleryId())
															 .galleryName(publishFileGroupRequest.getGalleryName())
															 .galleryDescription(publishFileGroupRequest.getGalleryDescription())
															 .tags(tagRequests)
															 .location(locationResponse)
															 .copyright(copyrightResponse)
															 .build();

					sortOrder++;

					if (imageVideoDimensions != null) {
						fileIngestRequest.setWidth(imageVideoDimensions.width);
						fileIngestRequest.setHeight(imageVideoDimensions.height);
					}

					if (fileEntity.getFileType()
								  .equals(FileTypeEnum.VIDEO)) {
						var videoFileEntity = (VideoFileEntity) fileEntity;
						fileIngestRequest.setLength(videoFileEntity.getDuration());
					} else if (fileEntity.getFileType()
										 .equals(FileTypeEnum.AUDIO)) {
						var audioFileEntity = (AudioFileEntity) fileEntity;
						fileIngestRequest.setLength(audioFileEntity.getDuration());
					} else if (fileEntity.getFileType()
										 .equals(FileTypeEnum.DOCUMENT)) {
						var documentFileEntity = (DocumentFileEntity) fileEntity;
						fileIngestRequest.setPages(documentFileEntity.getPageCount());
					}

					log.debug("Publishing {}", JsonTools.toJson(fileIngestRequest));
					// Upload with authentication retry (up to 5 attempts)
					final int maxRetries = 3;
					int       attempt    = 0;

					while (true) {
						try {
							var fileIngestResponse = vempainAdminService.uploadAsSiteFile(uploadPath.toFile(), fileIngestRequest);
							galleryId = fileIngestResponse.getGalleryId();
							log.debug("Published file {} from group {} as site file to gallery ID {}", exportFilePath.getFileName(), publishFileGroupRequest.getFileGroupId(),
									  galleryId);
							break; // success
						} catch (VempainAuthenticationException authEx) {
							attempt++;
							if (attempt >= maxRetries) {
								log.error("Authentication failed after {} attempts for file {} in group {}", attempt, exportFilePath.getFileName(), publishFileGroupRequest.getFileGroupId());
								throw authEx;
							}
							log.warn("Authentication failed (attempt {}/{}). Re-authenticating and retrying...", attempt, maxRetries);
							// Force re-login and retry
							vempainAdminTokenProvider.login();
						}
					}
				} catch (Exception ex) {
					log.error("Failed to publish file {} from group {}", exportFilePath.getFileName(), publishFileGroupRequest.getFileGroupId(), ex);
				} finally {
					// Cleanup temp image if created
					if (tempPathToDelete != null) {
						try {
							Files.deleteIfExists(tempPathToDelete);
						} catch (IOException ioe) {
							log.warn("Failed to delete temp file {}", tempPathToDelete, ioe);
						}
					}
				}
			}

			if (galleryId != null) {
				// Update the file group with the published gallery ID
				fileGroup.setGalleryId(galleryId);
				fileGroupRepository.save(fileGroup);
				log.info("File group {} published to gallery ID {}", publishFileGroupRequest.getFileGroupId(), galleryId);
			} else {
				log.warn("No files were published for group {}", publishFileGroupRequest.getFileGroupId());
			}

			progressStore.markCompleted(publishFileGroupRequest.getFileGroupId());
		} catch (Exception e) {
			log.error("Publish group {} failed", publishFileGroupRequest.getFileGroupId(), e);
			progressStore.markFailed(publishFileGroupRequest.getFileGroupId());
		}
	}

	/**
	 * Count files in a group without loading the full collection.
	 */
	public long countFilesInGroup(long fileGroupId) {
		return fileGroupRepository.countById(fileGroupId);
	}

	/**
	 * Triggers asynchronous publishing for all file groups. Returns the number of groups scheduled.
	 * Uses pagination to avoid loading all groups into memory at once.
	 */
	public long publishAllFileGroups() {
		int  page           = 0;
		int  size           = 50; // page size
		long scheduledCount = 0L;

		// Count total groups using repository count
		var totalGroups = fileGroupRepository.count();
		progressStore.init(totalGroups);

		var proxy = applicationContext.getBean(PublishService.class);

		while (true) {
			var pageable = PageRequest.of(page, size, Sort.by("path"));
			var pg       = fileGroupRepository.searchFileGroups(null, false, pageable);
			if (!pg.hasContent()) {
				break;
			}

			for (var projection : pg.getContent()) {
				var groupId = projection.id();
				var req = PublishFileGroupRequest.builder()
												 .fileGroupId(groupId)
												 .galleryName(projection.groupName())
												 .galleryDescription(projection.description() != null && projection.description()
																												   .length() > 2 ?
																	 projection.description() : projection.groupName())
												 .build();
				// mark scheduled
				progressStore.markScheduled(groupId);
				proxy.publishFileGroup(req);
				scheduledCount++;
			}

			page++;
			if (page >= pg.getTotalPages()) {
				break;
			}
		}

		return scheduledCount;
	}

	private Path resolveExportedPath(long fileId) {
		// Look up the exported file from export repository
		var optionalExportFileEntity = exportFileRepository.findByFileId(fileId);

		if (optionalExportFileEntity.isEmpty()) {
			log.warn("No exported file found for file entity with ID {}", fileId);
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

	private String normalizeIngestPath(String filePath) {
		if (filePath == null) {
			return "";
		}
		return filePath.startsWith("/") ? filePath.substring(1) : filePath;
	}
}
