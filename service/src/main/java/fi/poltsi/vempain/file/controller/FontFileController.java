package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.api.response.FontFileResponse;
import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.rest.FontFileAPI;
import fi.poltsi.vempain.file.service.FontFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FontFileController implements FontFileAPI {

	private final FontFileService fontFileService;

	@Override
	public ResponseEntity<PagedResponse<FontFileResponse>> findAll(int page, int size) {
		return ResponseEntity.ok(fontFileService.findAll(page, size));
	}

	@Override
	public ResponseEntity<FontFileResponse> findById(long id) {
		var response = fontFileService.findById(id);

		if (response == null) {
			return ResponseEntity.notFound()
								 .build();
		}

		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.status(fontFileService.delete(id))
							 .build();
	}
}
