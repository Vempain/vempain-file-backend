package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;


@Slf4j
@RequiredArgsConstructor
@Service
public class PublishService {
	private final VempainAdminService vempainAdminService;
	private final ImageTool           imageTool;
	private final FileGroupRepository fileGroupRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Value("${vempain.site-image-size:1200}")
	private int siteImageSize;

	@Async
	public void publishFileGroup(PublishFileGroupRequest request) {
		// Load group and files
		FileGroupEntity group = entityManager.find(FileGroupEntity.class, request.getFileGroupId());
		if (group == null) {
			log.warn("File group {} not found", request.getFileGroupId());
			return;
		}
		if (group.getFiles() == null || group.getFiles()
											 .isEmpty()) {
			log.info("File group {} has no files to publish", request.getFileGroupId());
			return;
		}

		for (FileEntity fileEntity : group.getFiles()) {
			Path sourcePath = resolveSourcePath(group, fileEntity);
			if (!Files.exists(sourcePath)) {
				log.warn("Source file does not exist, skipping: {}", sourcePath);
				continue;
			}

			boolean isImage          = isImageFileType(fileEntity);
			Path    uploadPath       = sourcePath;
			Path    tempPathToDelete = null;

			try {
				if (isImage) {
					// Create temp file with same extension in system temp dir
					String ext      = getExtension(fileEntity.getFilename());
					Path   tempFile = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "vempain-", ext.isBlank() ? ".tmp" : "." + ext);
					// Resize: smaller dimension to siteImageSize, keep quality 0.9
					imageTool.resizeImage(sourcePath, tempFile, siteImageSize, 0.7f);
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

	public int countFilesInGroup(long fileGroupId) {
		var optionalFileGroup = fileGroupRepository.findById(fileGroupId);

		if (optionalFileGroup.isEmpty()) {
			log.warn("File group with id {} not found", fileGroupId);
			return 0;
		}

		return optionalFileGroup.get()
								.getFiles()
								.size();
	}

	private Path resolveSourcePath(FileGroupEntity group, FileEntity fe) {
		// group.getPath() + fe.getFilePath() + fe.getFilename()
		String rel = fe.getFilePath() == null ? "" : fe.getFilePath();
		if (rel.startsWith("/")) {
			rel = rel.substring(1);
		}
		return Path.of(group.getPath())
				   .resolve(rel)
				   .resolve(fe.getFilename());
	}

	private boolean isImageFileType(FileEntity fe) {
		String t = fe.getFileType();
		return t != null && t.equalsIgnoreCase("IMAGE");
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
