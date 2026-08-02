package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.entity.Acl;
import fi.poltsi.vempain.auth.exception.VempainAclException;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.ThumbFileEntity;
import fi.poltsi.vempain.file.repository.files.ThumbFileRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailGenerationService {

	private final ThumbFileRepository thumbFileRepository;
	private final ImageTool           imageTool;
	private final AclService          aclService;

	@Transactional
	public void generateThumbnail(ExportFileEntity exportFile, String originalRootDirectory, String exportRootDirectory,
	                              int thumbnailMinimumSize, float thumbnailQuality)
			throws IOException, VempainAclException {
		var targetFile = exportFile.getFile();
		if (thumbFileRepository.existsByTargetFileId(targetFile.getId())) {
			return;
		}

		var sourcePath = resolveOriginalPath(exportFile, exportRootDirectory);
		if (!Files.isRegularFile(sourcePath)) {
			log.warn("Export image does not exist for file id={}: {}", exportFile.getId(), sourcePath);
			return;
		}

		var thumbnail = resolveThumbnail(exportFile, originalRootDirectory);
		Files.createDirectories(thumbnail.destinationPath()
		                                 .getParent());
		Path temporaryPath = Files.createTempFile(
				thumbnail.destinationPath()
				         .getParent(),
				"." + thumbnail.filename() + ".",
				".tmp.jpeg");
		try {
			Dimension dimensions = imageTool.resizeImage(
					sourcePath, temporaryPath, thumbnailMinimumSize, thumbnailQuality, null);
			Files.move(temporaryPath, thumbnail.destinationPath(),
			           StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

			var acl           = aclService.createUniqueAcl(targetFile.getCreator(), null, true, true, true, true);
			var thumbnailFile = createThumbnailEntity(targetFile, acl, thumbnail, dimensions);
			thumbFileRepository.saveAndFlush(thumbnailFile);
			log.info("Generated thumbnail for file id={} at {}", targetFile.getId(), thumbnail.relativePath());
		} finally {
			Files.deleteIfExists(temporaryPath);
		}
	}

	private ThumbFileEntity createThumbnailEntity(FileEntity targetFile, Acl acl, ThumbnailPath thumbnail,
	                                              Dimension dimensions) {
		var thumbnailFile = new ThumbFileEntity();
		thumbnailFile.setAclId(acl.getAclId());
		thumbnailFile.setCreated(Instant.now());
		thumbnailFile.setCreator(targetFile.getCreator());
		thumbnailFile.setExternalFileId("THUMB-" + targetFile.getId());
		thumbnailFile.setFilename(thumbnail.filename());
		thumbnailFile.setFilePath(thumbnail.filePath());
		thumbnailFile.setFileType(FileTypeEnum.THUMB);
		thumbnailFile.setFilesize(thumbnail.destinationPath()
		                                   .toFile()
		                                   .length());
		thumbnailFile.setMetadataRaw("{}");
		thumbnailFile.setMimetype("image/jpeg");
		var sha256sum = computeSha256(thumbnail.destinationPath()
		                                       .toFile());
		if (sha256sum == null) {
			throw new IllegalStateException("Failed to calculate thumbnail checksum: " + thumbnail.destinationPath());
		}
		thumbnailFile.setSha256sum(sha256sum);
		thumbnailFile.setTargetFile(targetFile);
		thumbnailFile.setRelationType("thumbnail");
		thumbnailFile.setDescription(dimensions.width + "x" + dimensions.height);
		return thumbnailFile;
	}

	private Path resolveOriginalPath(ExportFileEntity exportFile, String exportRootDirectory) {
		return Path.of(exportRootDirectory)
		           .resolve(normalizePath(exportFile.getFilePath()))
		           .resolve(exportFile.getFilename());
	}

	private ThumbnailPath resolveThumbnail(ExportFileEntity exportFile, String originalRootDirectory) {
		var relativeDirectory  = normalizePath(exportFile.getFilePath());
		var thumbnailFilename  = replaceExtension(exportFile.getFilename(), "jpeg");
		var thumbnailDirectory = "/thumb" + (relativeDirectory.isEmpty() ? "" : "/" + relativeDirectory);
		var relativePath       = thumbnailDirectory + "/" + thumbnailFilename;
		var destinationPath = Path.of(originalRootDirectory)
		                             .resolve(relativePath.substring(1));
		return new ThumbnailPath(relativePath, thumbnailDirectory, thumbnailFilename, destinationPath);
	}

	private String normalizePath(String value) {
		if (value == null || value.isBlank() || "/".equals(value)) {
			return "";
		}
		return value.startsWith("/") ? value.substring(1) : value;
	}

	private String replaceExtension(String filename, String extension) {
		var dot = filename.lastIndexOf('.');
		return (dot > 0 ? filename.substring(0, dot) : filename) + "." + extension;
	}

	private record ThumbnailPath(String relativePath, String filePath, String filename, Path destinationPath) {
	}
}
