package fi.poltsi.vempain.file.processing;

import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.file.configuration.VideoExportProperties;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileProcessingQueueEntity;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoFileQueueProcessorITC {
	@Test
	void encodesSampleVideoAndCreatesExportRecord(@TempDir Path tempDirectory) throws Exception {
		var originalRoot    = tempDirectory.resolve("original");
		var exportRoot      = tempDirectory.resolve("export");
		var sourceDirectory = originalRoot.resolve("videos");
		Files.createDirectories(sourceDirectory);
		var source = sourceDirectory.resolve("sample.mp4");
		createSampleVideo(source);

		var properties = new VideoExportProperties();
		ReflectionTestUtils.setField(properties, "width", 32);
		ReflectionTestUtils.setField(properties, "height", 24);
		ReflectionTestUtils.setField(properties, "fps", 10D);
		ReflectionTestUtils.setField(properties, "videoCodec", "mpeg4");
		ReflectionTestUtils.setField(properties, "container", "mp4");
		ReflectionTestUtils.setField(properties, "quality", 23);
		var exports   = mock(ExportFileRepository.class);
		var processor = new VideoFileQueueProcessor(exports, properties);
		ReflectionTestUtils.setField(processor, "originalRootDirectory", originalRoot.toString());
		ReflectionTestUtils.setField(processor, "exportRootDirectory", exportRoot.toString());

		var file = VideoFileEntity.builder()
								  .id(1L)
								  .filename("sample.mp4")
								  .filePath("/videos")
								  .fileType(FileTypeEnum.VIDEO)
								  .originalDocumentId("sample")
								  .build();
		var queueItem = FileProcessingQueueEntity.builder()
												 .file(file)
												 .fileType(FileTypeEnum.VIDEO)
												 .build();

		processor.process(queueItem);

		var encoded = exportRoot.resolve("videos/sample.mp4");
		assertThat(encoded).exists();
		assertThat(computeSha256(encoded.toFile())).isNotBlank();
		try (var grabber = new FFmpegFrameGrabber(encoded.toFile())) {
			grabber.start();
			assertThat(grabber.getImageWidth()).isEqualTo(32);
			assertThat(grabber.getImageHeight()).isEqualTo(24);
			assertThat(grabber.grab()).isNotNull();
			grabber.stop();
		}
		verify(exports).save(any());
	}

	private void createSampleVideo(Path target) throws Exception {
		try (var recorder = new FFmpegFrameRecorder(target.toString(), 16, 12);
			 var converter = new Java2DFrameConverter()) {
			recorder.setFormat("mp4");
			recorder.setFrameRate(10);
			recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4);
			recorder.start();
			for (int index = 0; index < 3; index++) {
				var image    = new BufferedImage(16, 12, BufferedImage.TYPE_3BYTE_BGR);
				var graphics = image.createGraphics();
				graphics.setColor(index % 2 == 0 ? Color.RED : Color.BLUE);
				graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
				graphics.dispose();
				recorder.record(converter.convert(image));
			}
			recorder.stop();
		}
	}


	private VideoExportProperties properties(String container, String videoCodec, String audioCodec) {
		var properties = new VideoExportProperties();
		ReflectionTestUtils.setField(properties, "width", 32);
		ReflectionTestUtils.setField(properties, "height", 24);
		ReflectionTestUtils.setField(properties, "fps", 10D);
		ReflectionTestUtils.setField(properties, "videoCodec", videoCodec);
		ReflectionTestUtils.setField(properties, "audioCodec", audioCodec);
		ReflectionTestUtils.setField(properties, "audioBitrate", 32000);
		ReflectionTestUtils.setField(properties, "container", container);
		ReflectionTestUtils.setField(properties, "quality", 23);
		return properties;
	}

	private static FileProcessingQueueEntity queueItem(String filePath, String filename) {
		return FileProcessingQueueEntity.builder()
										.file(VideoFileEntity.builder()
															 .id(1L)
															 .filename(filename)
															 .filePath(filePath)
															 .fileType(FileTypeEnum.VIDEO)
															 .build())
										.fileType(FileTypeEnum.VIDEO)
										.build();
	}

	@Test
	void reportsItsTypeSkipsExportedFilesAndRejectsMissingSources(@TempDir Path tempDirectory) throws Exception {
		var originalRoot = tempDirectory.resolve("original");
		var exportRoot   = tempDirectory.resolve("export");
		var exports      = mock(ExportFileRepository.class);
		var processor    = new VideoFileQueueProcessor(exports, properties("mp4", "mpeg4", "aac"));
		ReflectionTestUtils.setField(processor, "originalRootDirectory", originalRoot.toString());
		ReflectionTestUtils.setField(processor, "exportRootDirectory", exportRoot.toString());

		assertThat(processor.supportedType()).isEqualTo(FileTypeEnum.VIDEO);
		assertThatThrownBy(() -> processor.process(queueItem("/", "missing.mp4"))).isInstanceOf(IllegalStateException.class)
																				  .hasMessageContaining("missing.mp4");

		Files.createDirectories(originalRoot);
		createSampleVideo(originalRoot.resolve("done.mp4"));
		when(exports.findByFileId(1L)).thenReturn(Optional.of(ExportFileEntity.builder()
																			  .build()));
		processor.process(queueItem("/", "done.mp4"));
		assertThat(exportRoot.resolve("done.mp4")).doesNotExist();
		verify(exports, never()).save(any());
	}

	@Test
	void encodesIntoOtherContainersAndUnknownCodecsFail(@TempDir Path tempDirectory) throws Exception {
		var originalRoot = tempDirectory.resolve("original");
		var exportRoot   = tempDirectory.resolve("export");
		Files.createDirectories(originalRoot);
		createSampleVideo(originalRoot.resolve("talk.mp4"));
		var exports = mock(ExportFileRepository.class);

		var processor = new VideoFileQueueProcessor(exports, properties("matroska", "mpeg4", "aac"));
		ReflectionTestUtils.setField(processor, "originalRootDirectory", originalRoot.toString());
		ReflectionTestUtils.setField(processor, "exportRootDirectory", exportRoot.toString());
		processor.process(queueItem("/", "talk.mp4"));
		var encoded = exportRoot.resolve("talk.matroska");
		assertThat(encoded).exists();
		var saved = org.mockito.ArgumentCaptor.forClass(ExportFileEntity.class);
		verify(exports).save(saved.capture());
		assertThat(saved.getValue()
						.getMimetype()).isEqualTo("video/matroska");
		assertThat(saved.getValue()
						.getFilePath()).isEqualTo("/");

		var unknown = new VideoFileQueueProcessor(exports, properties("mp4", "no-such-codec", "aac"));
		ReflectionTestUtils.setField(unknown, "originalRootDirectory", originalRoot.toString());
		ReflectionTestUtils.setField(unknown, "exportRootDirectory", exportRoot.toString());
		assertThatThrownBy(() -> unknown.process(queueItem("/", "talk.mp4"))).isInstanceOf(IllegalArgumentException.class)
																			 .hasMessage("Unknown FFmpeg codec: no-such-codec");
		assertThat(exportRoot.resolve("talk.mp4.tmp")).doesNotExist();

		// libx264 is either bundled or replaced by mpeg4; both produce a file
		var h264 = new VideoFileQueueProcessor(exports, properties("mp4", "libx264", "aac"));
		ReflectionTestUtils.setField(h264, "originalRootDirectory", originalRoot.toString());
		ReflectionTestUtils.setField(h264, "exportRootDirectory", exportRoot.toString());
		h264.process(queueItem("/", "talk.mp4"));
		assertThat(exportRoot.resolve("talk.mp4")).exists();
	}
}
