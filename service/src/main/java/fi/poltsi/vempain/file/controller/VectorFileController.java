package fi.poltsi.vempain.file.controller;


import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.VectorFileResponse;
import fi.poltsi.vempain.file.rest.VectorFileAPI;
import fi.poltsi.vempain.file.service.VectorFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VectorFileController implements VectorFileAPI {

	private final VectorFileService vectorFileService;

	@Override
	public ResponseEntity<PagedResponse<VectorFileResponse>> findAll(int page, int size) {
		return ResponseEntity.ok(vectorFileService.findAll(page, size));
	}

	@Override
	public ResponseEntity<VectorFileResponse> findById(long id) {
		var response = vectorFileService.findById(id);

		if (response == null) {
			return ResponseEntity.notFound()
								 .build();
		}

		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.status(vectorFileService.delete(id))
							 .build();
	}
}
