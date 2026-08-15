package fi.poltsi.vempain.file.processing;

import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.configuration.VideoExportProperties;
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

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
