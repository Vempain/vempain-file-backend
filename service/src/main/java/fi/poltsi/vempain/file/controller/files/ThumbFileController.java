package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.ThumbFileResponse;
import fi.poltsi.vempain.file.rest.files.ThumbFileAPI;
import fi.poltsi.vempain.file.service.files.ThumbFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ThumbFileController implements ThumbFileAPI {

	private final ThumbFileService service;

	@Override
	public ResponseEntity<PagedResponse<ThumbFileResponse>> findAll(int page, int size) {
		return ResponseEntity.ok(service.findAll(page, size));
	}

	@Override
	public ResponseEntity<ThumbFileResponse> findById(long id) {
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

