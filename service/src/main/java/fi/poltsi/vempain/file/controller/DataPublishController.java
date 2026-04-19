package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.admin.api.response.DataResponse;
import fi.poltsi.vempain.file.rest.DataPublishAPI;
import fi.poltsi.vempain.file.service.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DataPublishController implements DataPublishAPI {

	private final DataService dataService;

	@Override
	public ResponseEntity<DataResponse> publishMusicDataset() {
		var result = dataService.generateAndPublishMusicDataset();
		return ResponseEntity.ok(result);
	}

	@Override
	public ResponseEntity<DataResponse> publishGpsTimeSeries(String directoryPath) {
		var result = dataService.generateAndPublishGpsTimeSeries(directoryPath);
		return ResponseEntity.ok(result);
	}
}
