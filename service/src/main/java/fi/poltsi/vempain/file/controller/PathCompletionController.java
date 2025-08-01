package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.api.request.PathCompletionRequest;
import fi.poltsi.vempain.file.api.response.PathCompletionResponse;
import fi.poltsi.vempain.file.rest.PathCompletionAPI;
import fi.poltsi.vempain.file.service.PathCompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PathCompletionController implements PathCompletionAPI {

    private final PathCompletionService pathCompletionService;

    @Override
    public ResponseEntity<PathCompletionResponse> completePath(PathCompletionRequest request) {
        PathCompletionResponse response = pathCompletionService.completePath(request);
        return ResponseEntity.ok(response);
    }
}
