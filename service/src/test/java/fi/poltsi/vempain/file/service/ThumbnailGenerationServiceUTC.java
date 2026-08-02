package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.entity.Acl;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.repository.files.ThumbFileRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
	private ThumbnailGenerationService service;

	@Test
	void rechecksExistingThumbnailBeforeGenerating() throws Exception {
		var target = new ImageFileEntity();
		target.setId(48111L);
		var export = ExportFileEntity.builder()
		                             .file(target)
		                             .filename("image.jpg")
		                             .build();
		when(thumbFileRepository.existsByTargetFileId(48111L)).thenReturn(true);

		service.generateThumbnail(export, "/tmp", 250, 0.7f);

		verify(imageTool, never()).resizeImage(any(), any(), any(Integer.class), any(Float.class), any());
		verify(thumbFileRepository, never()).saveAndFlush(any());
	}

	@Test
	void atomicallyPublishesGeneratedFileAndPersistsThumbnail() throws Exception {
		var root   = Files.createTempDirectory("thumbnail-root");
		var source = root.resolve("image/image.jpg");
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
		var acl = org.mockito.Mockito.mock(Acl.class);
		when(thumbFileRepository.existsByTargetFileId(48111L)).thenReturn(false);
		when(aclService.createUniqueAcl(eq(7L), eq(null), eq(true), eq(true), eq(true), eq(true))).thenReturn(acl);
		when(acl.getAclId()).thenReturn(99L);
		when(imageTool.resizeImage(eq(source), any(), eq(250), eq(0.7f), eq(null)))
				.thenAnswer(invocation -> {
					Files.writeString(invocation.getArgument(1, Path.class), "thumbnail");
					return new Dimension(300, 200);
				});

		service.generateThumbnail(export, root.toString(), 250, 0.7f);

		var captor = ArgumentCaptor.forClass(fi.poltsi.vempain.file.entity.ThumbFileEntity.class);
		verify(thumbFileRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue()
		                 .getTargetFile()).isSameAs(target);
		assertThat(captor.getValue()
		                 .getFileType()).isEqualTo(FileTypeEnum.THUMB);
		assertThat(Files.readString(root.resolve("thumb/image/image.jpeg"))).isEqualTo("thumbnail");
	}
}
