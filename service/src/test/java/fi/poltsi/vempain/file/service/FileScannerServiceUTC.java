package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.request.ScanRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class FileScannerServiceUTC {

	@Test
	void returnsResponseForEmptyScanRequest() {
		var service = new FileScannerService(mock(DirectoryProcessorService.class), mock(FileResponseEnricher.class));
		ReflectionTestUtils.setField(service, "originalRootDirectory", "/tmp");
		ReflectionTestUtils.setField(service, "exportRootDirectory", "/tmp");

		var response = service.scanDirectories(new ScanRequest());

		assertThat(response).isNotNull();
	}

	@Test
	void rejectsScanDirectoryOutsideConfiguredRoot() {
		var service = new FileScannerService(mock(DirectoryProcessorService.class), mock(FileResponseEnricher.class));
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
		var service = new FileScannerService(mock(DirectoryProcessorService.class), mock(FileResponseEnricher.class));
		ReflectionTestUtils.setField(service, "originalRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "exportRootDirectory", root.toString());
		var request = new ScanRequest();
		request.setOriginalDirectory(root.relativize(link)
		                                 .toString());

		assertThrows(ResponseStatusException.class, () -> service.scanDirectories(request));
	}
}
