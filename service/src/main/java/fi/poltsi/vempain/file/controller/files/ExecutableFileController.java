package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.ExecutableFileResponse;
import fi.poltsi.vempain.file.rest.files.ExecutableFileAPI;
import fi.poltsi.vempain.file.service.files.ExecutableFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExecutableFileController implements ExecutableFileAPI {

	private final ExecutableFileService service;

	@Override
	public ResponseEntity<PagedResponse<ExecutableFileResponse>> findAll(int page, int size) {
		return ResponseEntity.ok(service.findAll(page, size));
	}

	@Override
	public ResponseEntity<ExecutableFileResponse> findById(long id) {
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

