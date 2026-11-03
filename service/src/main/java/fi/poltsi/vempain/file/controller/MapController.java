package fi.poltsi.vempain.file.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import fi.poltsi.vempain.common.api.request.MapClusterFilesRequest;
import fi.poltsi.vempain.common.api.request.MapClusterRequest;
import fi.poltsi.vempain.common.api.response.MapClusterResponse;
import fi.poltsi.vempain.common.api.response.MapFileResponse;
import fi.poltsi.vempain.common.rest.MapAPI;
import fi.poltsi.vempain.file.service.MapService;
import lombok.RequiredArgsConstructor;

/** The map of the GPS-tagged original files ({@code MapAPI} of vempain-common, hosted at {@code /map}) */
@RestController
@RequiredArgsConstructor
public class MapController implements MapAPI {

    private final MapService mapService;

    @Override
    public ResponseEntity<MapClusterResponse> getClusters(MapClusterRequest request) {
        return ResponseEntity.ok(mapService.clusters(request));
    }

    @Override
    public ResponseEntity<List<MapFileResponse>> getClusterFiles(MapClusterFilesRequest request) {
        return ResponseEntity.ok(mapService.files(request));
    }
}
