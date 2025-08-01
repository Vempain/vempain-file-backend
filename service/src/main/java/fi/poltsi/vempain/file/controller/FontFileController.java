package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.FontFileResponse;
import fi.poltsi.vempain.file.service.FontFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FontFileController implements FontFileAPI {

	private final FontFileService fontFileService;

	@Override
	public ResponseEntity<List<FontFileResponse>> findAll() {
		return fontFileService.findAll();
	}

	@Override
	public ResponseEntity<FontFileResponse> findById(long id) {
		return fontFileService.findById(id);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return fontFileService.delete(id);
	}
}
