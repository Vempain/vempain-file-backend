package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.request.ScanRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
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
}
