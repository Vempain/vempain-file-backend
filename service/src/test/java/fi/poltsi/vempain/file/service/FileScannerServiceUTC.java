package fi.poltsi.vempain.file.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import fi.poltsi.vempain.common.api.TaskStatusEnum;
import fi.poltsi.vempain.common.task.TaskCancelledException;
import fi.poltsi.vempain.common.task.TaskProgress;
import fi.poltsi.vempain.common.task.TaskProgressStore;
import fi.poltsi.vempain.common.task.TaskRunner;
import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.api.response.ScanResponses;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.metrics.FileMetrics;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileScannerServiceUTC {

    @Test
    void returnsResponseForEmptyScanRequest() {
        var service = new FileScannerService(mock(DirectoryProcessorService.class), mock(FileResponseEnricher.class), mock(TaskRunner.class),
                mock(FileRepository.class), mock(ExportFileRepository.class), mock(FileGroupRepository.class), metrics());
        ReflectionTestUtils.setField(service, "originalRootDirectory", "/tmp");
        ReflectionTestUtils.setField(service, "exportRootDirectory", "/tmp");

        var response = service.scanDirectories(new ScanRequest());

        assertThat(response).isNotNull();
    }

    @Test
    void rejectsScanDirectoryOutsideConfiguredRoot() {
        var service = new FileScannerService(mock(DirectoryProcessorService.class), mock(FileResponseEnricher.class), mock(TaskRunner.class),
                mock(FileRepository.class), mock(ExportFileRepository.class), mock(FileGroupRepository.class), metrics());
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
                mock(FileRepository.class), mock(ExportFileRepository.class), mock(FileGroupRepository.class), metrics());
        ReflectionTestUtils.setField(service, "originalRootDirectory", root.toString());
        ReflectionTestUtils.setField(service, "exportRootDirectory", root.toString());
        var request = new ScanRequest();
        request.setOriginalDirectory(root.relativize(link)
            .toString());

        assertThrows(ResponseStatusException.class, () -> service.scanDirectories(request));
    }

    @Test
    void scanAsTaskValidatesDirectoriesBeforeSubmitting(@TempDir Path root) {
        var runner = mock(TaskRunner.class);
        var service = new FileScannerService(mock(DirectoryProcessorService.class), mock(FileResponseEnricher.class), runner, mock(FileRepository.class),
                mock(ExportFileRepository.class), mock(FileGroupRepository.class), metrics());
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
                mock(FileRepository.class), mock(ExportFileRepository.class), mock(FileGroupRepository.class), metrics());
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
        var files = mock(FileRepository.class);
        var exports = mock(ExportFileRepository.class);
        var groups = mock(FileGroupRepository.class);
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
                exports, groups, metrics());
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
                mock(FileRepository.class), mock(ExportFileRepository.class), mock(FileGroupRepository.class), metrics());

        assertThat(service.recorderFor(null)).isSameAs(ScanRecorder.NONE);
    }

    private FileScannerService serviceWithRoots(DirectoryProcessorService processor, String originalRoot, String exportRoot) {
        var service = new FileScannerService(processor, mock(FileResponseEnricher.class), mock(TaskRunner.class), mock(FileRepository.class),
                mock(ExportFileRepository.class), mock(FileGroupRepository.class), metrics());
        ReflectionTestUtils.setField(service, "originalRootDirectory", originalRoot);
        ReflectionTestUtils.setField(service, "exportRootDirectory", exportRoot);
        return service;
    }

    @Test
    void unreadableExportDirectoryIsReportedAsAFailedScan(@TempDir Path root) throws Exception {
        Assumptions.assumeFalse("root".equals(System.getProperty("user.name")), "root can read every directory");
        var export = Files.createDirectories(root.resolve("export"));
        var service = serviceWithRoots(mock(DirectoryProcessorService.class), root.toString(), root.toString());
        var request = new ScanRequest();
        request.setExportDirectory("/export");

        Files.setPosixFilePermissions(export, EnumSet.noneOf(PosixFilePermission.class));
        try {
            var response = service.scanDirectories(request)
                .getScanExportResponse();

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorMessage()).isEqualTo("Unable to scan the requested directory");
        } finally {
            Files.setPosixFilePermissions(export,
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        }
    }

    @Test
    void unavailableScanRootIsAnInternalError() {
        var service = serviceWithRoots(mock(DirectoryProcessorService.class), "/nonexistent-root-" + java.util.UUID.randomUUID(), "/tmp");
        var request = new ScanRequest();
        request.setOriginalDirectory("/photos");

        var failure = assertThrows(ResponseStatusException.class, () -> service.scanDirectories(request));

        assertThat(failure.getStatusCode()
            .value()).isEqualTo(500);
    }

    @Test
    void theRootDirectoryItselfCanBeScanned(@TempDir Path root) throws Exception {
        var processor = mock(DirectoryProcessorService.class);
        when(processor.processOriginalDirectory(any(), any(), any(), any(), any(ScanRecorder.class))).thenReturn(java.util.List.of(0L, 0L));
        var service = serviceWithRoots(processor, root.toString(), root.toString());
        var request = new ScanRequest();
        request.setOriginalDirectory("/");

        var response = service.scanDirectories(request)
            .getScanOriginalResponse();

        assertThat(response.isSuccess()).isTrue();
        org.mockito.Mockito.verify(processor)
            .processOriginalDirectory(org.mockito.ArgumentMatchers.eq(root.toRealPath()), any(), any(), any(), any(ScanRecorder.class));
    }

    @Test
    void recorderCheckpointsDelegateToTheTask() {
        var service = serviceWithRoots(mock(DirectoryProcessorService.class), "/tmp", "/tmp");
        var progress = TaskProgress.unmanaged("t", "SCAN_DIRECTORIES", "title", 1L, 1, "{}");

        var recorder = service.recorderFor(progress);
        recorder.checkpoint();

        assertThat(recorder).isNotSameAs(ScanRecorder.NONE);
        assertThat(progress.isCancelRequested()).isFalse();
    }

    private static FileMetrics metrics() {
        return new FileMetrics(new SimpleMeterRegistry());
    }
}
