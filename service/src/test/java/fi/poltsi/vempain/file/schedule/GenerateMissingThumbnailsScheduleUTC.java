package fi.poltsi.vempain.file.schedule;

import fi.poltsi.vempain.auth.entity.Acl;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.files.ThumbFileRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateMissingThumbnailsScheduleUTC {

	@Mock
	private ExportFileRepository              exportFileRepository;
	@Mock
	private ThumbFileRepository               thumbFileRepository;
	@Mock
	private ImageTool                         imageTool;
	@Mock
	private AclService                        aclService;
	@InjectMocks
	private GenerateMissingThumbnailsSchedule schedule;

	@Test
	void generateMissingThumbnails_createsJpegThumbnailAndPersistsRelation() throws Exception {
		var root   = Files.createTempDirectory("thumbnail-root");
		var source = root.resolve("image/Matkailu/Pohjola-2008/Pohjola-2008-0043.jpg");
		Files.createDirectories(source.getParent());
		Files.writeString(source, "source");

		var targetFile = new ImageFileEntity();
		targetFile.setId(48111L);
		targetFile.setCreator(7L);
		var exportFile = ExportFileEntity.builder()
		                                 .id(39884L)
		                                 .file(targetFile)
		                                 .filename("Pohjola-2008-0043.jpg")
		                                 .filePath("/image/Matkailu/Pohjola-2008")
		                                 .mimetype("image/jpeg")
		                                 .build();
		var acl = org.mockito.Mockito.mock(Acl.class);

		when(exportFileRepository.findImagesMissingThumbnails(any(Pageable.class))).thenReturn(List.of(exportFile));
		when(aclService.createUniqueAcl(eq(7L), isNull(), eq(true), eq(true), eq(true), eq(true))).thenReturn(acl);
		when(acl.getAclId()).thenReturn(99L);
		when(imageTool.resizeImage(eq(source), any(), eq(250), eq(0.7f), isNull()))
				.thenAnswer(invocation -> {
					var destination = invocation.getArgument(1, Path.class);
					Files.writeString(destination, "thumbnail");
					return new Dimension(300, 200);
				});

		ReflectionTestUtils.setField(schedule, "batchSize", 1000);
		ReflectionTestUtils.setField(schedule, "schedulerEnabled", true);
		ReflectionTestUtils.setField(schedule, "thumbnailQuality", 0.7f);
		ReflectionTestUtils.setField(schedule, "thumbnailMinimumSize", 250);
		ReflectionTestUtils.setField(schedule, "originalRootDirectory", root.toString());
		schedule.generateMissingThumbnails();

		var captor = ArgumentCaptor.forClass(fi.poltsi.vempain.file.entity.ThumbFileEntity.class);
		verify(thumbFileRepository).save(captor.capture());
		var thumbnail = captor.getValue();
		assertThat(thumbnail.getTargetFile()).isSameAs(targetFile);
		assertThat(thumbnail.getFileType()).isEqualTo(FileTypeEnum.THUMB);
		assertThat(thumbnail.getFilePath() + "/" + thumbnail.getFilename())
				.isEqualTo("/thumb/image/Matkailu/Pohjola-2008/Pohjola-2008-0043.jpeg");
		assertThat(thumbnail.getFilename()).isEqualTo("Pohjola-2008-0043.jpeg");
		assertThat(thumbnail.getFilePath()).isEqualTo("/thumb/image/Matkailu/Pohjola-2008");
		assertThat(thumbnail.getMimetype()).isEqualTo("image/jpeg");
		assertThat(Files.exists(root.resolve("thumb/image/Matkailu/Pohjola-2008/Pohjola-2008-0043.jpeg"))).isTrue();

		var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(exportFileRepository).findImagesMissingThumbnails(pageableCaptor.capture());
		assertThat(pageableCaptor.getValue()
		                         .getPageSize()).isEqualTo(1000);
	}
}
