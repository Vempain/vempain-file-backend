package fi.poltsi.vempain.file.controller.files;


import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.VectorFileResponse;
import fi.poltsi.vempain.file.rest.files.VectorFileAPI;
import fi.poltsi.vempain.file.service.files.VectorFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VectorFileController implements VectorFileAPI {

	private final VectorFileService vectorFileService;

	@Override
	public ResponseEntity<PagedResponse<VectorFileResponse>> findAll(PagedRequest pagedRequest) {
		return ResponseEntity.ok(vectorFileService.findAll(pagedRequest));
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
