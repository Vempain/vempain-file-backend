package fi.poltsi.vempain.file.controller.files;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.InteractiveFileResponse;
import fi.poltsi.vempain.file.rest.files.InteractiveFileAPI;
import fi.poltsi.vempain.file.service.files.InteractiveFileService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class InteractiveFileController implements InteractiveFileAPI {

    private final InteractiveFileService service;

    @Override
    public ResponseEntity<PagedResponse<InteractiveFileResponse>> findAll(PagedRequest pagedRequest) {
        return ResponseEntity.ok(service.findAll(pagedRequest));
    }

    @Override
    public ResponseEntity<InteractiveFileResponse> findById(long id) {
        var response = service.findById(id);
        return response == null ? ResponseEntity.notFound()
            .build() : ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> delete(long id) {
        return ResponseEntity.status(service.delete(id))
            .build();
    }
}
