package fi.poltsi.vempain.file.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileThumbnailEntity;
import fi.poltsi.vempain.file.metrics.FileMetrics;
import fi.poltsi.vempain.file.metrics.ToolMetrics;
import fi.poltsi.vempain.file.repository.FileThumbnailRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailGenerationService {

    private final FileThumbnailRepository thumbnailRepository;
    private final ImageTool imageTool;
    private final FileMetrics fileMetrics;

    @Transactional
    public void generateThumbnail(ExportFileEntity exportFile, String originalRootDirectory, String exportRootDirectory, int thumbnailMinimumSize,
            float thumbnailQuality) throws IOException {
        generateThumbnail(exportFile, originalRootDirectory, exportRootDirectory, thumbnailMinimumSize, thumbnailQuality, 0.3f);
    }

    @Transactional
    public void generateThumbnail(ExportFileEntity exportFile, String originalRootDirectory, String exportRootDirectory, int thumbnailMinimumSize,
            float thumbnailQuality, float videoCapturePercentage) throws IOException {
        var targetFile = exportFile.getFile();
        if (thumbnailRepository.existsByFileId(targetFile.getId())) {
            return;
        }

        var sourcePath = resolveOriginalPath(exportFile, exportRootDirectory);
        if (!Files.isRegularFile(sourcePath)) {
            log.warn("Export file does not exist for file id={}: {}", exportFile.getId(), sourcePath);
            return;
        }

        var thumbnail = resolveThumbnail(exportFile, originalRootDirectory);
        Files.createDirectories(thumbnail.destinationPath()
            .getParent());
        Path videoFramePath = null;
        Path temporaryPath = Files.createTempFile(thumbnail.destinationPath()
            .getParent(), "." + thumbnail.filename() + ".", ".tmp.jpeg");
        var generated = false;
        try {
            if (targetFile.getFileType() == FileTypeEnum.VIDEO) {
                videoFramePath = Files.createTempFile("video-thumbnail-frame-", ".jpeg");
                extractVideoFrame(sourcePath, videoFramePath, videoCapturePercentage);
                sourcePath = videoFramePath;
            }
            imageTool.resizeImage(sourcePath, temporaryPath, thumbnailMinimumSize, thumbnailQuality, null);
            Files.move(temporaryPath, thumbnail.destinationPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            var thumbnailFile = createThumbnailEntity(targetFile, thumbnail);
            thumbnailRepository.saveAndFlush(thumbnailFile);
            generated = true;
            log.info("Generated thumbnail for file id={} at {}", targetFile.getId(), thumbnail.relativePath());
        } finally {
            fileMetrics.thumbnailGenerated(generated);
            Files.deleteIfExists(temporaryPath);
            if (videoFramePath != null) {
                Files.deleteIfExists(videoFramePath);
            }
        }
    }

    private void extractVideoFrame(Path sourcePath, Path targetPath, float capturePercentage) throws IOException {
        if (capturePercentage < 0
            || capturePercentage > 1) {
            throw new IllegalArgumentException("Video capture percentage must be between 0 and 1");
        }

        ToolMetrics.time(ToolMetrics.FFMPEG, () -> {
            grabFrame(sourcePath, targetPath, capturePercentage);
            return null;
        });
    }

    private void grabFrame(Path sourcePath, Path targetPath, float capturePercentage) throws IOException {
        try (var grabber = new FFmpegFrameGrabber(sourcePath.toFile()); var converter = new Java2DFrameConverter()) {
            grabber.setImageMode(org.bytedeco.javacv.FrameGrabber.ImageMode.COLOR);
            grabber.start();
            var timestamp = Math.round(grabber.getLengthInTime() * capturePercentage);
            grabber.setTimestamp(timestamp);
            var frame = grabber.grab();
            if (frame != null
                && frame.imageChannels > 4) {
                // JavaCV derives channels from the aligned stride for very small frames.
                frame.imageChannels = 3;
            }
            BufferedImage image = frame == null ? null : converter.getBufferedImage(frame);
            if (image == null
                || !ImageIO.write(image, "jpeg", targetPath.toFile())) {
                throw new IOException("Could not extract a video frame from " + sourcePath);
            }
            grabber.stop();
        }
    }

    private FileThumbnailEntity createThumbnailEntity(FileEntity targetFile, ThumbnailPath thumbnail) throws IOException {
        var image = ImageIO.read(thumbnail.destinationPath()
            .toFile());
        var thumbnailFile = new FileThumbnailEntity();
        thumbnailFile.setCreated(java.time.Instant.now());
        thumbnailFile.setFilename(thumbnail.filename());
        thumbnailFile.setFilePath(thumbnail.relativePath());
        thumbnailFile.setFilesize(thumbnail.destinationPath()
            .toFile()
            .length());
        thumbnailFile.setMimetype("image/jpeg");
        var sha256sum = computeSha256(thumbnail.destinationPath()
            .toFile());
        if (sha256sum == null) {
            throw new IllegalStateException("Failed to calculate thumbnail checksum: " + thumbnail.destinationPath());
        }
        thumbnailFile.setSha256sum(sha256sum);
        thumbnailFile.setFile(targetFile);
        if (image != null) {
            thumbnailFile.setWidth(image.getWidth());
            thumbnailFile.setHeight(image.getHeight());
        }
        return thumbnailFile;
    }

    private Path resolveOriginalPath(ExportFileEntity exportFile, String exportRootDirectory) {
        return Path.of(exportRootDirectory)
            .resolve(normalizePath(exportFile.getFilePath()))
            .resolve(exportFile.getFilename());
    }

    private ThumbnailPath resolveThumbnail(ExportFileEntity exportFile, String originalRootDirectory) {
        var relativeDirectory = normalizePath(exportFile.getFilePath());
        var thumbnailFilename = replaceExtension(exportFile.getFilename(), "jpeg");
        var thumbnailDirectory = "/thumb" + (relativeDirectory.isEmpty() ? "" : "/" + relativeDirectory);
        var relativePath = thumbnailDirectory + "/" + thumbnailFilename;
        var destinationPath = Path.of(originalRootDirectory)
            .resolve(relativePath.substring(1));
        return new ThumbnailPath(relativePath, thumbnailDirectory, thumbnailFilename, destinationPath);
    }

    private String normalizePath(String value) {
        if (value == null
            || value.isBlank()
            || "/".equals(value)) {
            return "";
        }
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private String replaceExtension(String filename, String extension) {
        var dot = filename.lastIndexOf('.');
        return (dot > 0 ? filename.substring(0, dot) : filename) + "." + extension;
    }

    private record ThumbnailPath(String relativePath, String filePath, String filename, Path destinationPath) {}
}
