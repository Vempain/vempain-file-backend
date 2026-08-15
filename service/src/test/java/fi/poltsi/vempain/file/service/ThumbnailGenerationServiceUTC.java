package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.entity.Acl;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.files.ThumbFileRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThumbnailGenerationServiceUTC {

	@Mock
	private ThumbFileRepository        thumbFileRepository;
	@Mock
	private ImageTool                  imageTool;
	@Mock
	private AclService                 aclService;
	@InjectMocks
	private ThumbnailGenerationService thumbnailGenerationService;

	@Test
	void rechecksExistingThumbnailBeforeGenerating() throws Exception {
		var target = new ImageFileEntity();
		target.setId(48111L);
		var export = ExportFileEntity.builder()
		                             .file(target)
		                             .filename("image.jpg")
		                             .build();
		when(thumbFileRepository.existsByTargetFileId(48111L)).thenReturn(true);

		thumbnailGenerationService.generateThumbnail(export, "/tmp", "/tmp", 250, 0.7f);

		verify(imageTool, never()).resizeImage(any(), any(), any(Integer.class), any(Float.class), any());
		verify(thumbFileRepository, never()).saveAndFlush(any());
	}

	@Test
	void atomicallyPublishesGeneratedFileAndPersistsThumbnail() throws Exception {
		var originalDir = Files.createTempDirectory("thumbnail-original");
		var exportDir   = Files.createTempDirectory("thumbnail-export");
		var source      = exportDir.resolve("image/image.jpg");
		Files.createDirectories(source.getParent());
		Files.writeString(source, "source");
		var target = new ImageFileEntity();
		target.setId(48111L);
		target.setCreator(7L);
		var export = ExportFileEntity.builder()
		                             .file(target)
		                             .filename("image.jpg")
		                             .filePath("/image")
		                             .build();
		var acl = mock(Acl.class);
		when(thumbFileRepository.existsByTargetFileId(48111L))
				.thenReturn(false);
		when(aclService.createUniqueAcl(eq(7L), eq(null), eq(true), eq(true), eq(true), eq(true)))
				.thenReturn(acl);
		when(acl.getAclId())
				.thenReturn(99L);
		when(imageTool.resizeImage(eq(source), any(), eq(250), eq(0.7f), eq(null)))
				.thenAnswer(invocation -> {
					Files.writeString(invocation.getArgument(1, Path.class), "thumbnail");
					return new Dimension(300, 200);
				});

		thumbnailGenerationService.generateThumbnail(export, originalDir.toString(), exportDir.toString(), 250, 0.7f);

		var captor = ArgumentCaptor.forClass(fi.poltsi.vempain.file.entity.ThumbFileEntity.class);
		verify(thumbFileRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue()
		                 .getTargetFile()).isSameAs(target);
		assertThat(captor.getValue()
		                 .getFileType()).isEqualTo(FileTypeEnum.THUMB);
		assertThat(Files.readString(originalDir.resolve("thumb/image/image.jpeg"))).isEqualTo("thumbnail");
	}

	@Test
	void extractsVideoFrameAndStoresJpegThumbnail() throws Exception {
		var originalDir = Files.createTempDirectory("thumbnail-video-original");
		var exportDir   = Files.createTempDirectory("thumbnail-video-export");
		var source      = exportDir.resolve("video/sample.mp4");
		Files.createDirectories(source.getParent());
		createSampleVideo(source);
		var target = new VideoFileEntity();
		target.setId(48112L);
		target.setCreator(7L);
		target.setFileType(FileTypeEnum.VIDEO);
		var export = ExportFileEntity.builder()
									 .file(target)
									 .filename("sample.mp4")
									 .filePath("/video")
									 .build();
		var acl = mock(Acl.class);
		when(thumbFileRepository.existsByTargetFileId(48112L)).thenReturn(false);
		when(aclService.createUniqueAcl(eq(7L), eq(null), eq(true), eq(true), eq(true), eq(true))).thenReturn(acl);
		when(acl.getAclId()).thenReturn(99L);
		when(imageTool.resizeImage(any(), any(), eq(250), eq(0.7f), eq(null))).thenAnswer(invocation -> {
			var input  = ImageIO.read(invocation.getArgument(0, Path.class)
			                                    .toFile());
			var output = invocation.getArgument(1, Path.class);
			ImageIO.write(input, "jpeg", output.toFile());
			return new Dimension(input.getWidth(), input.getHeight());
		});

		thumbnailGenerationService.generateThumbnail(export, originalDir.toString(), exportDir.toString(), 250, 0.7f, 0.3f);

		var thumbnail = originalDir.resolve("thumb/video/sample.jpeg");
		assertThat(ImageIO.read(thumbnail.toFile())).isNotNull();
		verify(thumbFileRepository).saveAndFlush(any());
	}

	private void createSampleVideo(Path target) throws Exception {
		try (var recorder = new FFmpegFrameRecorder(target.toString(), 16, 12);
			 var converter = new Java2DFrameConverter()) {
			recorder.setFormat("mp4");
			recorder.setFrameRate(10);
			recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4);
			recorder.start();
			for (var color : new Color[]{Color.RED, Color.BLUE, Color.GREEN}) {
				var image    = new BufferedImage(16, 12, BufferedImage.TYPE_3BYTE_BGR);
				var graphics = image.createGraphics();
				graphics.setColor(color);
				graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
				graphics.dispose();
				recorder.record(converter.convert(image));
			}
			recorder.stop();
		}
	}
}
