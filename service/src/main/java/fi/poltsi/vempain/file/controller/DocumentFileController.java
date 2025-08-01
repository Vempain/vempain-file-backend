package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.DocumentFileResponse;
import fi.poltsi.vempain.file.service.DocumentFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DocumentFileController implements DocumentFileAPI {

	private final DocumentFileService documentFileService;

	@Override
	public ResponseEntity<List<DocumentFileResponse>> findAll() {
		return documentFileService.findAll();
	}

	@Override
	public ResponseEntity<DocumentFileResponse> findById(long id) {
		return documentFileService.findById(id);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return documentFileService.delete(id);
	}
}
