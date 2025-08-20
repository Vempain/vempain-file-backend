package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.admin.api.FileClassEnum;
import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
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
	private final VempainAdminService  vempainAdminService;
	private final ImageTool            imageTool;
	private final FileGroupRepository  fileGroupRepository;
	private final ExportFileRepository exportFileRepository;

	@Value("${vempain.site-image-size:1200}")
	private int siteImageSize;

	@Value("${vempain.export-root-directory}")
	private String exportRootDirectory;

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

		for (FileEntity fileEntity : fileGroup.getFiles()) {
			var exportFilePath = resolveExportedPath(fileEntity.getId());

			if (exportFilePath == null
				|| !Files.exists(exportFilePath)) {
				log.warn("Export file does not exist, skipping: {}", exportFilePath);
				continue;
			}

			boolean isImage          = isImageFileType(fileEntity);
			Path uploadPath = exportFilePath;
			Path    tempPathToDelete = null;

			try {
				if (isImage) {
					// Create temp file with same extension in system temp dir
					String ext = getExtension(exportFilePath.toString());
					Path   tempFile = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "vempain-", ext.isBlank() ? ".tmp" : "." + ext);
					// Resize: smaller dimension to siteImageSize, keep quality 0.9
					imageTool.resizeImage(exportFilePath, tempFile, siteImageSize, 0.7f);
					uploadPath       = tempFile;
					tempPathToDelete = tempFile;
				}

				// Build ingest request
				FileIngestRequest ingest = new FileIngestRequest();
				ingest.setFileName(fileEntity.getFilename());
				ingest.setFilePath(normalizeIngestPath(fileEntity.getFilePath())); // relative and no leading slash
				ingest.setMimeType(fileEntity.getMimetype());
				ingest.setComment(""); // optional but not-null in DTO
				ingest.setMetadata(fileEntity.getMetadataRaw() != null ? fileEntity.getMetadataRaw() : "{}");
				ingest.setSha256sum(computeSha256(uploadPath.toFile()));
				ingest.setUserId(0L); // service context; adjust if a real user id is available
				ingest.setGalleryId(null);
				ingest.setGalleryName(request.getGalleryName());
				ingest.setGalleryDescription(request.getGalleryDescription());

				// Upload
				vempainAdminService.uploadAsSiteFile(uploadPath.toFile(), ingest);
			} catch (Exception ex) {
				log.error("Failed to publish file {} from group {}", fileEntity.getFilename(), request.getFileGroupId(), ex);
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

	private boolean isImageFileType(FileEntity fe) {
		String t = fe.getFileType();
		return t != null && t.equalsIgnoreCase(FileClassEnum.IMAGE.toString());
	}

	private String getExtension(String filename) {
		if (filename == null) {
			return "";
		}
		int idx = filename.lastIndexOf('.');
		if (idx < 0 || idx == filename.length() - 1) {
			return "";
		}
		return filename.substring(idx + 1);
	}

	private String normalizeIngestPath(String filePath) {
		if (filePath == null) {
			return "";
		}
		return filePath.startsWith("/") ? filePath.substring(1) : filePath;
	}
}
