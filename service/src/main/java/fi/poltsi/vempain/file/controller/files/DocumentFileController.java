package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.DocumentFileResponse;
import fi.poltsi.vempain.file.rest.files.DocumentFileAPI;
import fi.poltsi.vempain.file.service.files.DocumentFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DocumentFileController implements DocumentFileAPI {

	private final DocumentFileService documentFileService;

	@Override
	public ResponseEntity<PagedResponse<DocumentFileResponse>> findAll(int page, int size) {
		return ResponseEntity.ok(documentFileService.findAll(page, size));
	}

	@Override
	public ResponseEntity<DocumentFileResponse> findById(long id) {
		var response = documentFileService.findById(id);

		if (response == null) {
			return ResponseEntity.notFound()
								 .build();
		}

		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.status(documentFileService.delete(id))
							 .build();
	}
}
