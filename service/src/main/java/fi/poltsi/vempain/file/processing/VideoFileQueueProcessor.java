package fi.poltsi.vempain.file.processing;

import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.configuration.VideoExportProperties;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileProcessingQueueEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoFileQueueProcessor implements FileQueueProcessor {
	private final ExportFileRepository  exportFileRepository;
	private final VideoExportProperties properties;

	@Value("${vempain.original-root-directory}")
	private String originalRootDirectory;
	@Value("${vempain.export-root-directory}")
	private String exportRootDirectory;

	@Override
	public FileTypeEnum supportedType() {
		return FileTypeEnum.VIDEO;
	}

	@Override
	public void process(FileProcessingQueueEntity queueItem) throws Exception {
		var source = originalPath(queueItem);
		if (!Files.isRegularFile(source)) {
			throw new IllegalStateException("Video source does not exist: " + source);
		}
		if (exportFileRepository.findByFileId(queueItem.getFile()
													   .getId())
								.isPresent()) {
			return;
		}

		var output = outputPath(queueItem);
		Files.createDirectories(output.getParent());
		var temporaryOutput = output.resolveSibling(output.getFileName() + ".tmp");
		Files.deleteIfExists(temporaryOutput);
		try {
			encode(source, temporaryOutput);
			Files.move(temporaryOutput, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			var export = ExportFileEntity.builder()
										 .file(queueItem.getFile())
										 .filename(output.getFileName()
														 .toString())
										 .filePath(queueItem.getFile()
															.getFilePath())
										 .mimetype(mimetype())
										 .filesize(Files.size(output))
										 .sha256sum(computeSha256(output.toFile()))
										 .originalDocumentId(queueItem.getFile()
																	  .getOriginalDocumentId())
										 .created(Instant.now())
										 .build();
			exportFileRepository.save(export);
		} finally {
			Files.deleteIfExists(temporaryOutput);
		}
	}

	private void encode(Path source, Path output) throws Exception {
		try (var grabber = new FFmpegFrameGrabber(source.toFile());
			 var recorder = new FFmpegFrameRecorder(output.toString(), properties.getWidth(), properties.getHeight(),
													grabber.getAudioChannels())) {
			grabber.start();
			recorder.setFormat(properties.getContainer());
			recorder.setFrameRate(properties.getFps());
			recorder.setVideoCodec(videoCodecId(properties.getVideoCodec()));
			recorder.setVideoOption("crf", Integer.toString(properties.getQuality()));
			if (grabber.getAudioChannels() > 0) {
				recorder.setAudioCodec(codecId(properties.getAudioCodec()));
				recorder.setAudioBitrate(properties.getAudioBitrate());
			}
			recorder.start();
			Frame frame;
			while ((frame = grabber.grab()) != null) {
				recorder.record(frame);
			}
			recorder.stop();
			grabber.stop();
		}
	}

	private int codecId(String codecName) {
		var encoderName = "ogg".equalsIgnoreCase(codecName) ? "vorbis" : codecName;
		var codec       = avcodec_find_encoder_by_name(encoderName);
		if (codec == null) {
			throw new IllegalArgumentException("Unknown FFmpeg codec: " + codecName);
		}
		return codec.id();
	}

	private int videoCodecId(String codecName) {
		try {
			return codecId(codecName);
		} catch (IllegalArgumentException exception) {
			if (!"libx264".equalsIgnoreCase(codecName)) {
				throw exception;
			}
			log.warn("Bundled JavaCV FFmpeg does not provide {}; falling back to mpeg4", codecName);
			return codecId("mpeg4");
		}
	}

	private Path originalPath(FileProcessingQueueEntity item) {
		return Path.of(originalRootDirectory)
				   .resolve(normalize(item.getFile()
										  .getFilePath()))
				   .resolve(item.getFile()
								.getFilename());
	}

	private Path outputPath(FileProcessingQueueEntity item) {
		var sourceName = item.getFile()
							 .getFilename();
		var dot        = sourceName.lastIndexOf('.');
		var outputName = (dot > 0 ? sourceName.substring(0, dot) : sourceName) + "." + properties.getContainer();
		return Path.of(exportRootDirectory)
				   .resolve(normalize(item.getFile()
										  .getFilePath()))
				   .resolve(outputName);
	}

	private String mimetype() {
		return switch (properties.getContainer()
								 .toLowerCase()) {
			case "mkv" -> "video/x-matroska";
			case "ogv", "ogg" -> "video/ogg";
			default -> "video/" + properties.getContainer()
											.toLowerCase();
		};
	}

	private String normalize(String value) {
		if (value == null || value.isBlank() || "/".equals(value)) {
			return "";
		}
		return value.startsWith("/") ? value.substring(1) : value;
	}
}
