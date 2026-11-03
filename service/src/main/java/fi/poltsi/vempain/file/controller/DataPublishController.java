package fi.poltsi.vempain.file.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.common.api.response.TaskAcceptedResponse;
import fi.poltsi.vempain.file.api.request.CreateGpsTimeSeriesRequest;
import fi.poltsi.vempain.file.api.request.DataSetPagedRequest;
import fi.poltsi.vempain.file.api.request.PublishMusicDatasetRequest;
import fi.poltsi.vempain.file.api.response.DataSetSummaryResponse;
import fi.poltsi.vempain.file.api.response.PublishDataSetStatusResponse;
import fi.poltsi.vempain.file.rest.DataPublishAPI;
import fi.poltsi.vempain.file.service.DataService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class DataPublishController implements DataPublishAPI {

    private final DataService dataService;

    @Override
    public ResponseEntity<TaskAcceptedResponse> publishMusicDataset(PublishMusicDatasetRequest request) {
        var publishToSite = request != null
                            && Boolean.TRUE.equals(request.getPublishToSite());
        return ResponseEntity.accepted()
            .body(dataService.publishMusicDatasetAsTask(publishToSite, request == null ? null : request.getAcls())
                .toAcceptedResponse());
    }

    @Override
    public ResponseEntity<TaskAcceptedResponse> publishGpsTimeSeries(CreateGpsTimeSeriesRequest request) {
        var publishToSite = Boolean.TRUE.equals(request.getPublishToSite());
        return ResponseEntity.accepted()
            .body(dataService.publishGpsTimeSeriesByFileGroupAsTask(request.getFileGroupId(), request.getTimeSeriesName(), publishToSite, request.getAcls())
                .toAcceptedResponse());
    }

    @Override
    public ResponseEntity<PagedResponse<DataSetSummaryResponse>> getPagedDataSets(DataSetPagedRequest request) {
        return ResponseEntity.ok(dataService.pagedDataSets(request));
    }

    @Override
    public ResponseEntity<PublishDataSetStatusResponse> getDataSetStatus(String identifier) {
        return ResponseEntity.ok(dataService.dataSetStatus(identifier));
    }
}
