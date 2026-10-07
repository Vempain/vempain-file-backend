package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.TaskStatusEnum;
import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.api.response.ScanResponses;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.task.TaskCancelledException;
import fi.poltsi.vempain.file.task.TaskProgressStore;
import fi.poltsi.vempain.file.task.TaskRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileScannerServiceUTC {

	@Test
	void returnsResponseForEmptyScanRequest() {
		var service = new FileScannerService(mock(DirectoryProcessorService.class), mock(FileResponseEnricher.class), mock(TaskRunner.class),
											 mock(FileRepository.class), mock(ExportFileRepository.class), mock(FileGroupRepository.class));
		ReflectionTestUtils.setField(service, "originalRootDirectory", "/tmp");
		ReflectionTestUtils.setField(service, "exportRootDirectory", "/tmp");

		var response = service.scanDirectories(new ScanRequest());

		assertThat(response).isNotNull();
	}

	@Test
	void rejectsScanDirectoryOutsideConfiguredRoot() {
		var service = new FileScannerService(mock(DirectoryProcessorService.class), mock(FileResponseEnricher.class), mock(TaskRunner.class),
											 mock(FileRepository.class), mock(ExportFileRepository.class), mock(FileGroupRepository.class));
		ReflectionTestUtils.setField(service, "originalRootDirectory", "/tmp");
		ReflectionTestUtils.setField(service, "exportRootDirectory", "/tmp");
		var request = new ScanRequest();
		request.setOriginalDirectory("../../etc");

		assertThrows(ResponseStatusException.class, () -> service.scanDirectories(request));
	}

	@Test
	void rejectsScanDirectoryThroughSymlink(@TempDir Path root) throws Exception {
		Path link;
		try {
			link = Files.createSymbolicLink(root.resolve("linked"), root.getParent());
		} catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
			return;
		}
		var service = new FileScannerService(mock(DirectoryProcessorService.class), mock(FileResponseEnricher.class), mock(TaskRunner.class),
											 mock(FileRepository.class), mock(ExportFileRepository.class), mock(FileGroupRepository.class));
		ReflectionTestUtils.setField(service, "originalRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "exportRootDirectory", root.toString());
		var request = new ScanRequest();
		request.setOriginalDirectory(root.relativize(link)
		                                 .toString());

		assertThrows(ResponseStatusException.class, () -> service.scanDirectories(request));
	}

	@Test
	void scanAsTaskValidatesDirectoriesBeforeSubmitting(@TempDir Path root) {
		var runner  = mock(TaskRunner.class);
		var service = new FileScannerService(mock(DirectoryProcessorService.class), mock(FileResponseEnricher.class), runner, mock(FileRepository.class),
											 mock(ExportFileRepository.class), mock(FileGroupRepository.class));
		ReflectionTestUtils.setField(service, "originalRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "exportRootDirectory", root.toString());
		var request = new ScanRequest();
		request.setOriginalDirectory("/missing");

		assertThrows(ResponseStatusException.class, () -> service.scanDirectoriesAsTask(request));
		org.mockito.Mockito.verifyNoInteractions(runner);
	}

	@Test
	void scanAsTaskReportsALeafDirectoryPerStepAndReturnsTheScanResult(@TempDir Path root) throws Exception {
		Files.createDirectories(root.resolve("photos/2024"));
		Files.createDirectories(root.resolve("photos/2025"));
		var processor = mock(DirectoryProcessorService.class);
		when(processor.processOriginalDirectory(any(), any(), any(), any(), any(ScanRecorder.class))).thenReturn(java.util.List.of(2L, 2L));
		var service = new FileScannerService(processor, mock(FileResponseEnricher.class), new TaskRunner(new TaskProgressStore(), Runnable::run),
											 mock(FileRepository.class), mock(ExportFileRepository.class), mock(FileGroupRepository.class));
		ReflectionTestUtils.setField(service, "originalRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "exportRootDirectory", root.toString());
		var request = new ScanRequest();
		request.setOriginalDirectory("/photos");

		var task = service.scanDirectoriesAsTask(request);

		assertThat(task.getType()).isEqualTo("SCAN_DIRECTORIES");
		assertThat(task.getTitle()).isEqualTo("Scan /photos");
		assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
		assertThat(task.getTotalSteps()
					   .get()).isEqualTo(2);
		assertThat(task.getCompletedSteps()
					   .get()).isEqualTo(2);
		assertThat(task.getMessage()).startsWith("Scanned ");
		assertThat(task.getResult()).isInstanceOf(ScanResponses.class);
		assertThat(((ScanResponses) task.getResult()).getScanOriginalResponse()
													 .getScannedFilesCount()).isEqualTo(4);
	}

	@Test
	void cancelledScanRemovesTheEntitiesItCreated(@TempDir Path root) throws Exception {
		Files.createDirectories(root.resolve("photos/2024"));
		var processor = mock(DirectoryProcessorService.class);
		var files     = mock(FileRepository.class);
		var exports   = mock(ExportFileRepository.class);
		var groups    = mock(FileGroupRepository.class);
		var createdFile = ImageFileEntity.builder()
										 .id(70L)
										 .filename("a.jpg")
										 .build();
		var createdGroup = FileGroupEntity.builder()
										  .id(7L)
										  .groupName("2024")
										  .build();
		var createdExport = ExportFileEntity.builder()
											.id(700L)
											.filename("a.jpg")
											.build();
		// The processor creates a group and a file, then the owner cancels: the next checkpoint stops the scan
		when(processor.processOriginalDirectory(any(), any(), any(), any(), any(ScanRecorder.class))).thenAnswer(invocation -> {
			ScanRecorder recorder = invocation.getArgument(4);
			recorder.fileGroupCreated(createdGroup);
			recorder.fileCreated(createdFile);
			recorder.exportFileCreated(createdExport);
			throw new TaskCancelledException();
		});
		var service = new FileScannerService(processor, mock(FileResponseEnricher.class), new TaskRunner(new TaskProgressStore(), Runnable::run), files,
											 exports, groups);
		ReflectionTestUtils.setField(service, "originalRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "exportRootDirectory", root.toString());
		var request = new ScanRequest();
		request.setOriginalDirectory("/photos");

		var task = service.scanDirectoriesAsTask(request);

		assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.CANCELLED);
		assertThat(task.getRevertedSteps()
					   .get()).isEqualTo(3);
		var order = org.mockito.Mockito.inOrder(exports, files, groups);
		order.verify(exports)
			 .deleteById(700L);
		order.verify(files)
			 .deleteById(70L);
		order.verify(groups)
			 .deleteById(7L);
	}

	@Test
	void recorderWithoutATaskDoesNothing() {
		var service = new FileScannerService(mock(DirectoryProcessorService.class), mock(FileResponseEnricher.class), mock(TaskRunner.class),
											 mock(FileRepository.class), mock(ExportFileRepository.class), mock(FileGroupRepository.class));

		assertThat(service.recorderFor(null)).isSameAs(ScanRecorder.NONE);
	}
}
