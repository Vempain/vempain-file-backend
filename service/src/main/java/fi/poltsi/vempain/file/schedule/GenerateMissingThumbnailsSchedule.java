package fi.poltsi.vempain.file.schedule;

import fi.poltsi.vempain.auth.entity.Acl;
import fi.poltsi.vempain.auth.exception.VempainAclException;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.ThumbFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.files.ThumbFileRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateMissingThumbnailsSchedule {


	private final ExportFileRepository exportFileRepository;
	private final ThumbFileRepository  thumbFileRepository;
	private final ImageTool            imageTool;
	private final AclService           aclService;

	@Value("${vempain.generate-missing-thumbnails.batch-size}")
	private int     batchSize = 1000;
	@Value("${vempain.generate-missing-thumbnails.enabled:true}")
	private boolean schedulerEnabled;
	@Value("${vempain.generate-missing-thumbnails.thumb-image-quality}")
	private float   thumbnailQuality;
	@Value("${vempain.generate-missing-thumbnails.thumb-image-size}")
	private int     thumbnailMinimumSize;
	@Value("${vempain.export-root-directory}")
	private String  exportRootDirectory;

	@Scheduled(cron = "${vempain.generate-missing-thumbnails.cron:0 0 * * * *}")
	@Transactional
	public void generateMissingThumbnailsScheduled() {
		if (!schedulerEnabled) {
			return;
		}
		generateMissingThumbnails();
	}

	public void generateMissingThumbnails() {
		var exportFiles = exportFileRepository.findImagesMissingThumbnails(PageRequest.of(0, batchSize));
		log.info("Generating missing thumbnails for {} image files", exportFiles.size());

		for (var exportFile : exportFiles) {
			try {
				generateThumbnail(exportFile);
			} catch (Exception e) {
				log.error("Failed to generate thumbnail for export file id={} file={}",
				          exportFile.getId(), exportFile.getFilename(), e);
			}
		}
	}

	private void generateThumbnail(ExportFileEntity exportFile) throws IOException, VempainAclException {
		var sourcePath = resolveOriginalPath(exportFile);
		if (!Files.isRegularFile(sourcePath)) {
			log.warn("Original image does not exist for export file id={}: {}", exportFile.getId(), sourcePath);
			return;
		}

		var thumbnail = resolveThumbnail(exportFile);
		Files.createDirectories(thumbnail.destinationPath()
		                                 .getParent());
		Dimension dimensions = imageTool.resizeImage(
				sourcePath,
				thumbnail.destinationPath(),
				thumbnailMinimumSize,
				thumbnailQuality,
				null);

		var targetFile    = exportFile.getFile();
		var acl           = aclService.createUniqueAcl(targetFile.getCreator(), null, true, true, true, true);
		var thumbnailFile = createThumbnailEntity(targetFile, acl, thumbnail, dimensions);
		thumbFileRepository.save(thumbnailFile);

		log.info("Generated thumbnail for file id={} at {}", targetFile.getId(), thumbnail.relativePath());
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

	private Path resolveOriginalPath(ExportFileEntity exportFile) {
		return Path.of(exportRootDirectory)
		           .resolve(normalizePath(exportFile.getFilePath()))
		           .resolve(exportFile.getFilename());
	}

	private ThumbnailPath resolveThumbnail(ExportFileEntity exportFile) {
		var relativeDirectory  = normalizePath(exportFile.getFilePath());
		var thumbnailFilename  = replaceExtension(exportFile.getFilename(), "jpeg");
		var thumbnailDirectory = "/thumb" + (relativeDirectory.isEmpty() ? "" : "/" + relativeDirectory);
		var relativePath       = thumbnailDirectory + "/" + thumbnailFilename;
		var destinationPath = Path.of(exportRootDirectory)
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
