package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.common.api.response.TaskAcceptedResponse;
import fi.poltsi.vempain.file.api.request.ScanRequest;
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
	public ResponseEntity<TaskAcceptedResponse> scan(ScanRequest scanRequest) {
		return ResponseEntity.accepted()
							 .body(fileScannerService.scanDirectoriesAsTask(scanRequest)
													 .toAcceptedResponse());
	}
}
