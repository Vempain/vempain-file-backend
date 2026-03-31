package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.BinaryFileResponse;
import fi.poltsi.vempain.file.rest.files.BinaryFileAPI;
import fi.poltsi.vempain.file.service.files.BinaryFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BinaryFileController implements BinaryFileAPI {

	private final BinaryFileService service;

	@Override
	public ResponseEntity<PagedResponse<BinaryFileResponse>> findAll(PagedRequest pagedRequest) {
		return ResponseEntity.ok(service.findAll(pagedRequest));
	}

	@Override
	public ResponseEntity<BinaryFileResponse> findById(long id) {
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

