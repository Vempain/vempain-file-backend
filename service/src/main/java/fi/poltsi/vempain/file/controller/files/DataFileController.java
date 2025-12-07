package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.DataFileResponse;
import fi.poltsi.vempain.file.rest.files.DataFileAPI;
import fi.poltsi.vempain.file.service.files.DataFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DataFileController implements DataFileAPI {

	private final DataFileService service;

	@Override
	public ResponseEntity<PagedResponse<DataFileResponse>> findAll(int page, int size) {
		return ResponseEntity.ok(service.findAll(page, size));
	}

	@Override
	public ResponseEntity<DataFileResponse> findById(long id) {
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

