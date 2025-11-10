package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.api.request.FileIngestRequest;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.response.CopyrightResponse;
import fi.poltsi.vempain.file.api.response.LocationResponse;
import fi.poltsi.vempain.file.feign.VempainAdminTokenProvider;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.LocationRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
import fi.poltsi.vempain.file.tools.MetadataTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;


@Slf4j
@RequiredArgsConstructor
@Service
public class PublishService {
	private final VempainAdminService       vempainAdminService;
	private final ImageTool                 imageTool;
	private final FileGroupRepository       fileGroupRepository;
	private final ExportFileRepository      exportFileRepository;
	private final VempainAdminTokenProvider vempainAdminTokenProvider;
	private final TagService tagService;
	private final LocationRepository locationRepository;

	@Value("${vempain.site-image-size:1200}")
	private int siteImageSize;

	@Value("${vempain.export-root-directory}")
	private String exportRootDirectory;

	@Value("${vempain.export-file-type}")
	private String exportFileType;

	@Async
	@Transactional
	public void publishFileGroup(PublishFileGroupRequest request) {
		// Load group and files
		var optionalGroup = fileGroupRepository.findById(request.getFileGroupId());
		if (optionalGroup.isEmpty()) {
			log.warn("File group {} not found", request.getFileGroupId());
			return;
		}

		var fileGroup = optionalGroup.get();

		if (fileGroup.getFiles() == null
			|| fileGroup.getFiles()
						.isEmpty()) {
			log.info("File group {} has no files to publish", request.getFileGroupId());
			return;
		}

		for (var fileEntity : fileGroup.getFiles()) {
			var exportFilePath = resolveExportedPath(fileEntity.getId());
			var siteFileName = fileEntity.getFilename();

			if (exportFilePath == null
				|| !Files.exists(exportFilePath)) {
				log.warn("Export file does not exist, skipping: {}", exportFilePath);
				continue;
			}

			Path uploadPath = exportFilePath;
			Path    tempPathToDelete = null;

			// Fetch the tags belonging to the file
			var tagRequests = tagService.getTagRequestsByFileId(fileEntity.getId());

			try {
				if (fileEntity.getFileType()
							  .equals(FileTypeEnum.IMAGE)) {
					// Create temp file with same extension in system temp dir
					Path tempFile = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "vempain-", "." + exportFileType);
					// Resize: smaller dimension to siteImageSize, keep quality 0.9
					imageTool.resizeImage(exportFilePath, tempFile, siteImageSize, 0.7f);
					tempPathToDelete = tempFile;
					exportFilePath = tempFile;
					uploadPath     = tempFile;

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
				LocationResponse locationResponse    = null;
				// Use relation from FileEntity instead of repository lookup
				if (fileEntity.getGpsLocation() != null) {
					locationResponse = fileEntity.getGpsLocation()
												 .toResponse();
				}

				var fileIngestRequest = FileIngestRequest.builder()
														 .fileName(siteFileName)
														 .filePath(normalizeIngestPath(fileEntity.getFilePath()))
														 .mimeType(mimetype)
														 .comment(fileEntity.getDescription() != null ? fileEntity.getDescription() : "")
														 .metadata(fileEntity.getMetadataRaw() != null ? fileEntity.getMetadataRaw() :
																   "{}")
														 .sha256sum(computeSha256(uploadPath.toFile()))
														 .originalDateTime(fileEntity.getOriginalDatetime())
														 .galleryId(null)
														 .galleryName(request.getGalleryName())
														 .galleryDescription(request.getGalleryDescription())
														 .tags(tagRequests)
														 .location(locationResponse)
														 .copyright(copyrightResponse)
														 .build();
				// Upload with authentication retry (up to 5 attempts)
				final int maxRetries = 3;
				int       attempt    = 0;

				while (true) {
					try {
						vempainAdminService.uploadAsSiteFile(uploadPath.toFile(), fileIngestRequest);
						break; // success
					} catch (VempainAuthenticationException authEx) {
						attempt++;
						if (attempt >= maxRetries) {
							log.error("Authentication failed after {} attempts for file {} in group {}", attempt, exportFilePath.getFileName(), request.getFileGroupId());
							throw authEx;
						}
						log.warn("Authentication failed (attempt {}/{}). Re-authenticating and retrying...", attempt, maxRetries);
						// Force re-login and retry
						vempainAdminTokenProvider.login();
					}
				}
			} catch (Exception ex) {
				log.error("Failed to publish file {} from group {}", exportFilePath.getFileName(), request.getFileGroupId(), ex);
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
	}

	// Helpers

	public long countFilesInGroup(long fileGroupId) {
		return fileGroupRepository.countById(fileGroupId);
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
