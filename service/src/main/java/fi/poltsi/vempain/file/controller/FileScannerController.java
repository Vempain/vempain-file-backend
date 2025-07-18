package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.api.response.ScanResponse;
import fi.poltsi.vempain.file.rest.FileScannerAPI;
import fi.poltsi.vempain.file.service.FileScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class FileScannerController implements FileScannerAPI {
	private final FileScannerService fileScannerService;

	@Override
	public ResponseEntity<ScanResponse> scan(ScanRequest scanRequest) {
		var scanResponse = fileScannerService.scanDirectory(scanRequest.getDirectoryName());
		return ResponseEntity.ok(scanResponse);
	}
}
