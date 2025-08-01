package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.ImageFileResponse;
import fi.poltsi.vempain.file.service.ImageFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ImageFileController implements ImageFileAPI {

	private final ImageFileService imageFileService;

	@Override
	public ResponseEntity<List<ImageFileResponse>> findAll() {
		return imageFileService.findAll();
	}

	@Override
	public ResponseEntity<ImageFileResponse> findById(long id) {
		return imageFileService.findById(id);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return imageFileService.delete(id);
	}
}
