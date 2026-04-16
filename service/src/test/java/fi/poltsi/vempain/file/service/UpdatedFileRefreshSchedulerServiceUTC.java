package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.admin.api.response.file.SiteFileResponse;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.SchedulerCheckpointRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdatedFileRefreshSchedulerServiceUTC {

	@Mock
	private FileRepository                fileRepository;
	@Mock
	private ExportFileRepository          exportFileRepository;
	@Mock
	private SchedulerCheckpointRepository schedulerCheckpointRepository;
	@Mock
	private DirectoryProcessorService     directoryProcessorService;
	@Mock
	private PublishService                publishService;
	@Mock
	private VempainAdminService           vempainAdminService;

	@Test
	void runRefresh_updatesChangedFile_andRepublishesSiteFileWhenKnownPublished() throws Exception {
		var root     = Files.createTempDirectory("refresh-root");
		var filePath = root.resolve("updated.jpg");
		Files.writeString(filePath, "new-content");

		var fileEntity = new ImageFileEntity();
		fileEntity.setId(1L);
		fileEntity.setFilePath("/");
		fileEntity.setFilename("updated.jpg");
		fileEntity.setFileType(FileTypeEnum.IMAGE);
		fileEntity.setSha256sum("old-sha");
		fileEntity.setSiteFilePublished(true);

		when(fileRepository.findAll()).thenReturn(List.of(fileEntity));
		when(fileRepository.findById(1L)).thenReturn(Optional.of(fileEntity));
		when(schedulerCheckpointRepository.findById("updated_file_refresh")).thenReturn(Optional.empty());
		when(directoryProcessorService.refreshExistingOriginalFile(eq(fileEntity), eq(filePath.toFile()))).thenReturn(true);
		when(exportFileRepository.findByFileId(1L)).thenReturn(Optional.empty());

		var service = new UpdatedFileRefreshSchedulerService(fileRepository,
		                                                     exportFileRepository,
		                                                     schedulerCheckpointRepository,
		                                                     directoryProcessorService,
		                                                     publishService,
		                                                     vempainAdminService);
		ReflectionTestUtils.setField(service, "originalRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "exportRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "exportFileType", "jpeg");

		service.runRefresh();

		verify(directoryProcessorService).refreshExistingOriginalFile(eq(fileEntity), eq(filePath.toFile()));
		verify(publishService).republishSiteFile(fileEntity);
		verify(schedulerCheckpointRepository).save(any());
	}

	@Test
	void runRefresh_resolvesUnknownSitePublication_andSkipsFileUpdateWhenShaUnchanged() throws Exception {
		var root     = Files.createTempDirectory("refresh-root-known");
		var filePath = root.resolve("known.jpg");
		Files.writeString(filePath, "same-content");

		var fileEntity = new ImageFileEntity();
		fileEntity.setId(2L);
		fileEntity.setFilePath("/");
		fileEntity.setFilename("known.jpg");
		fileEntity.setFileType(FileTypeEnum.IMAGE);
		fileEntity.setSha256sum(computeSha256(filePath.toFile()));
		fileEntity.setSiteFilePublished(null);

		var siteFileResponse = SiteFileResponse.builder()
		                                       .fileName("known.jpg")
		                                       .filePath("")
		                                       .build();
		var pagedResponse = new PagedResponse<SiteFileResponse>();
		pagedResponse.setContent(List.of(siteFileResponse));
		pagedResponse.setPage(0);
		pagedResponse.setSize(50);
		pagedResponse.setTotalElements(1);
		pagedResponse.setTotalPages(1);
		pagedResponse.setFirst(true);
		pagedResponse.setLast(true);

		when(fileRepository.findAll()).thenReturn(List.of(fileEntity));
		when(schedulerCheckpointRepository.findById("updated_file_refresh")).thenReturn(Optional.of(fi.poltsi.vempain.file.entity.SchedulerCheckpointEntity.builder()
		                                                                                                                                                   .taskName("updated_file_refresh")
		                                                                                                                                                   .lastChecked(Instant.EPOCH)
		                                                                                                                                                   .build()));
		when(vempainAdminService.getPageableSiteFiles(eq(FileTypeEnum.IMAGE), eq(0), eq(50), eq("id"), eq(Sort.Direction.ASC), eq("known.jpg"), any()))
				.thenReturn(pagedResponse);

		var service = new UpdatedFileRefreshSchedulerService(fileRepository,
		                                                     exportFileRepository,
		                                                     schedulerCheckpointRepository,
		                                                     directoryProcessorService,
		                                                     publishService,
		                                                     vempainAdminService);
		ReflectionTestUtils.setField(service, "originalRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "exportRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "exportFileType", "jpeg");

		service.runRefresh();

		verify(fileRepository).save(fileEntity);
		verify(directoryProcessorService, never()).refreshExistingOriginalFile(any(), any());
		verify(publishService, never()).republishSiteFile(any());
	}
}

