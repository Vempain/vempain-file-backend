package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.common.api.response.TaskAcceptedResponse;
import fi.poltsi.vempain.file.api.request.CreateGpsTimeSeriesRequest;
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
	public ResponseEntity<TaskAcceptedResponse> publishMusicDataset() {
		return ResponseEntity.accepted()
							 .body(dataService.publishMusicDatasetAsTask()
											  .toAcceptedResponse());
	}

	@Override
	public ResponseEntity<TaskAcceptedResponse> publishGpsTimeSeries(CreateGpsTimeSeriesRequest request) {
		return ResponseEntity.accepted()
							 .body(dataService.publishGpsTimeSeriesByFileGroupAsTask(request.getFileGroupId(), request.getTimeSeriesName())
											  .toAcceptedResponse());
	}
}
