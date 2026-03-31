package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.ImageFileResponse;
import fi.poltsi.vempain.file.rest.files.ImageFileAPI;
import fi.poltsi.vempain.file.service.files.ImageFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ImageFileController implements ImageFileAPI {

	private final ImageFileService imageFileService;

	@Override
	public ResponseEntity<PagedResponse<ImageFileResponse>> findAll(PagedRequest pagedRequest) {
		return ResponseEntity.ok(imageFileService.findAll(pagedRequest));
	}

	@Override
	public ResponseEntity<ImageFileResponse> findById(long id) {
		var response = imageFileService.findById(id);

		if (response == null) {
			return ResponseEntity.notFound()
								 .build();
		}

		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.status(imageFileService.delete(id))
							 .build();
	}
}
