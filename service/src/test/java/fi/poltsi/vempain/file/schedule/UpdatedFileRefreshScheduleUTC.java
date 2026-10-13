package fi.poltsi.vempain.file.schedule;

import fi.poltsi.vempain.admin.api.response.file.SiteFileResponse;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.SchedulerCheckpointRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.service.DirectoryProcessorService;
import fi.poltsi.vempain.file.service.PublishService;
import fi.poltsi.vempain.file.service.VempainAdminService;
import fi.poltsi.vempain.file.tools.SampleFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdatedFileRefreshScheduleUTC {

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
	@InjectMocks
	private UpdatedFileRefreshSchedule updatedFileRefreshSchedule;

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

		ReflectionTestUtils.setField(updatedFileRefreshSchedule, "originalRootDirectory", root.toString());
		ReflectionTestUtils.setField(updatedFileRefreshSchedule, "exportRootDirectory", root.toString());
		ReflectionTestUtils.setField(updatedFileRefreshSchedule, "exportFileType", "jpeg");

		updatedFileRefreshSchedule.runRefresh();

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

		ReflectionTestUtils.setField(updatedFileRefreshSchedule, "originalRootDirectory", root.toString());
		ReflectionTestUtils.setField(updatedFileRefreshSchedule, "exportRootDirectory", root.toString());
		ReflectionTestUtils.setField(updatedFileRefreshSchedule, "exportFileType", "jpeg");

		updatedFileRefreshSchedule.runRefresh();

		verify(fileRepository).save(fileEntity);
		verify(directoryProcessorService, never()).refreshExistingOriginalFile(any(), any());
		verify(publishService, never()).republishSiteFile(any());
	}

	@Test
	void scheduledRefreshDoesNothingWhenDisabled() {
		ReflectionTestUtils.setField(updatedFileRefreshSchedule, "schedulerEnabled", false);

		updatedFileRefreshSchedule.refreshUpdatedFilesScheduled();

		verifyNoInteractions(fileRepository, schedulerCheckpointRepository);
	}

	@Test
	void runRefreshSkipsMissingSourceAndOldFiles() throws Exception {
		var root    = Files.createTempDirectory("refresh-root-missing");
		var missing = new ImageFileEntity();
		missing.setFilePath("/");
		missing.setFilename("missing.jpg");
		var old = new ImageFileEntity();
		old.setFilePath("/");
		old.setFilename("old.jpg");
		var oldPath = root.resolve("old.jpg");
		Files.writeString(oldPath, "old");
		old.setSha256sum("different");

		when(fileRepository.findAll()).thenReturn(List.of(missing, old));
		when(schedulerCheckpointRepository.findById("updated_file_refresh"))
				.thenReturn(Optional.of(fi.poltsi.vempain.file.entity.SchedulerCheckpointEntity.builder()
																							   .taskName("updated_file_refresh")
																							   .lastChecked(Instant.now()
				                                                                                                   .plusSeconds(60))
																							   .build()));
		ReflectionTestUtils.setField(updatedFileRefreshSchedule, "originalRootDirectory", root.toString());

		updatedFileRefreshSchedule.runRefresh();

		verify(directoryProcessorService, never()).refreshExistingOriginalFile(any(), any());
		verify(schedulerCheckpointRepository).save(any());
	}


	private ImageFileEntity image(long id, String filename, Boolean sitePublished, String sha256) {
		var entity = new ImageFileEntity();
		entity.setId(id);
		entity.setFilePath("/");
		entity.setFilename(filename);
		entity.setFileType(FileTypeEnum.IMAGE);
		entity.setSha256sum(sha256);
		entity.setSiteFilePublished(sitePublished);
		return entity;
	}

	private void configureRoots(String root) {
		ReflectionTestUtils.setField(updatedFileRefreshSchedule, "originalRootDirectory", root);
		ReflectionTestUtils.setField(updatedFileRefreshSchedule, "exportRootDirectory", root);
		ReflectionTestUtils.setField(updatedFileRefreshSchedule, "exportFileType", "jpeg");
	}

	@Test
	void scheduledRefreshRunsWhenEnabled() throws Exception {
		var root = Files.createTempDirectory("refresh-root-scheduled");
		configureRoots(root.toString());
		ReflectionTestUtils.setField(updatedFileRefreshSchedule, "schedulerEnabled", true);
		when(fileRepository.findAll()).thenReturn(List.of());
		when(schedulerCheckpointRepository.findById("updated_file_refresh")).thenReturn(Optional.empty());

		updatedFileRefreshSchedule.refreshUpdatedFilesScheduled();

		verify(schedulerCheckpointRepository).save(any());
	}

	@Test
	void refreshThatReportsNothingOrFailsDoesNotRepublish() throws Exception {
		var root = Files.createTempDirectory("refresh-root-failures");
		Files.writeString(root.resolve("unchanged.jpg"), "a");
		Files.writeString(root.resolve("failing.jpg"), "b");
		var unchanged = image(1L, "unchanged.jpg", true, "old");
		var failing   = image(2L, "failing.jpg", true, "old");
		when(fileRepository.findAll()).thenReturn(List.of(unchanged, failing));
		when(schedulerCheckpointRepository.findById("updated_file_refresh")).thenReturn(Optional.empty());
		when(directoryProcessorService.refreshExistingOriginalFile(eq(unchanged), any())).thenReturn(false);
		when(directoryProcessorService.refreshExistingOriginalFile(eq(failing), any())).thenThrow(new IllegalStateException("exiftool"));
		configureRoots(root.toString());

		updatedFileRefreshSchedule.runRefresh();

		verify(publishService, never()).republishSiteFile(any());
		verify(schedulerCheckpointRepository).save(any());
	}

	@Test
	void sitePublicationDetectionHandlesUnknownTypesEmptyAnswersAndMismatches() throws Exception {
		var root     = Files.createTempDirectory("refresh-root-detect");
		var unknown  = image(3L, "unknown.bin", null, null);
		var empty    = image(4L, "empty.jpg", null, null);
		var mismatch = image(5L, "mismatch.jpg", null, null);
		for (var entity : List.of(unknown, empty, mismatch)) {
			var path = root.resolve(entity.getFilename());
			Files.writeString(path, entity.getFilename());
			entity.setSha256sum(computeSha256(path.toFile()));
		}
		unknown.setFileType(FileTypeEnum.UNKNOWN);
		var elsewhere = new PagedResponse<SiteFileResponse>();
		elsewhere.setContent(List.of(SiteFileResponse.builder()
													 .fileName("mismatch.jpg")
													 .filePath("/elsewhere")
													 .build()));
		org.mockito.Mockito.lenient()
						   .when(vempainAdminService.getPageableSiteFiles(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), any(), any()))
						   .thenReturn(null);
		org.mockito.Mockito.lenient()
						   .when(vempainAdminService.getPageableSiteFiles(eq(FileTypeEnum.IMAGE), eq(0), eq(50), eq("id"), eq(Sort.Direction.ASC), eq("mismatch.jpg"), any()))
						   .thenReturn(elsewhere);
		when(fileRepository.findAll()).thenReturn(List.of(unknown, empty, mismatch));
		when(schedulerCheckpointRepository.findById("updated_file_refresh")).thenReturn(Optional.empty());
		configureRoots(root.toString());

		updatedFileRefreshSchedule.runRefresh();

		assertThat(unknown.getSiteFilePublished()).isFalse();
		assertThat(empty.getSiteFilePublished()).isFalse();
		assertThat(mismatch.getSiteFilePublished()).isFalse();
		verify(fileRepository, org.mockito.Mockito.times(3)).save(any());
		verify(directoryProcessorService, never()).refreshExistingOriginalFile(any(), any());
	}

	@Test
	void linkedExportIsRefreshedOnlyWhenItExistsAndCanBeRead() throws Exception {
		var root      = Files.createTempDirectory("refresh-root-export");
		var refreshed = image(6L, "six.jpg", false, "old");
		refreshed.setOriginalDocumentId("doc-6");
		var missingExport = image(7L, "seven.jpg", false, "old");
		var unreadable    = image(8L, "eight.jpg", false, "old");
		var brokenLink    = image(9L, "nine.jpg", false, "old");
		for (var entity : List.of(refreshed, missingExport, unreadable, brokenLink)) {
			Files.writeString(root.resolve(entity.getFilename()), "new content " + entity.getId());
			when(directoryProcessorService.refreshExistingOriginalFile(eq(entity), any())).thenReturn(true);
		}
		var goodExport = ExportFileEntity.builder()
										 .file(refreshed)
										 .filePath("/")
										 .filename("six-export.jpg")
										 .sha256sum("stale")
										 .build();
		var exported = SampleFiles.jpeg(root.resolve("six-export.jpg"));
		when(exportFileRepository.findByFileId(6L)).thenReturn(Optional.of(goodExport));
		when(exportFileRepository.findByFileId(7L)).thenReturn(Optional.of(ExportFileEntity.builder()
																						   .file(missingExport)
																						   .filePath("/")
																						   .filename("not-there.jpg")
																						   .build()));
		Files.createDirectories(root.resolve("eight-export.jpg"));
		when(exportFileRepository.findByFileId(8L)).thenReturn(Optional.of(ExportFileEntity.builder()
																						   .file(unreadable)
																						   .filePath("/")
																						   .filename("eight-export.jpg")
																						   .build()));
		SampleFiles.jpeg(root.resolve("nine-export.jpg"));
		when(exportFileRepository.findByFileId(9L)).thenReturn(Optional.of(ExportFileEntity.builder()
																						   .filePath("/")
																						   .filename("nine-export.jpg")
																						   .build()));
		when(fileRepository.findAll()).thenReturn(List.of(refreshed, missingExport, unreadable, brokenLink));
		when(schedulerCheckpointRepository.findById("updated_file_refresh")).thenReturn(Optional.empty());
		configureRoots(root.toString());

		updatedFileRefreshSchedule.runRefresh();

		var saved = ArgumentCaptor.forClass(ExportFileEntity.class);
		verify(exportFileRepository).save(saved.capture());
		assertThat(saved.getValue()).isSameAs(goodExport);
		assertThat(goodExport.getSha256sum()).isEqualTo(computeSha256(exported));
		assertThat(goodExport.getFilesize()).isEqualTo(exported.length());
		assertThat(goodExport.getMimetype()).isEqualTo("image/jpeg");
		assertThat(goodExport.getOriginalDocumentId()).isEqualTo("doc-6");
		verify(publishService, never()).republishSiteFile(any());
	}
}
