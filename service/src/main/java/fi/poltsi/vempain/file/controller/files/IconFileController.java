package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.IconFileResponse;
import fi.poltsi.vempain.file.rest.files.IconFileAPI;
import fi.poltsi.vempain.file.service.files.IconFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class IconFileController implements IconFileAPI {

	private final IconFileService iconFileService;

	@Override
	public ResponseEntity<PagedResponse<IconFileResponse>> findAll(int page, int size) {
		return ResponseEntity.ok(iconFileService.findAll(page, size));
	}

	@Override
	public ResponseEntity<IconFileResponse> findById(long id) {
		var response = iconFileService.findById(id);

		if (response == null) {
			return ResponseEntity.notFound()
								 .build();
		}

		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.status(iconFileService.delete(id))
							 .build();
	}
}
