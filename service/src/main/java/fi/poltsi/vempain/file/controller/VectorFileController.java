package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.VectorFileResponse;
import fi.poltsi.vempain.file.service.VectorFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VectorFileController implements VectorFileAPI {

	private final VectorFileService vectorFileService;

	@Override
	public ResponseEntity<List<VectorFileResponse>> findAll() {
		return vectorFileService.findAll();
	}

	@Override
	public ResponseEntity<VectorFileResponse> findById(long id) {
		return vectorFileService.findById(id);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return vectorFileService.delete(id);
	}
}
