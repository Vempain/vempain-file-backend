package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.IconFileResponse;
import fi.poltsi.vempain.file.service.IconFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class IconFileController implements IconFileAPI {

	private final IconFileService iconFileService;

	@Override
	public ResponseEntity<List<IconFileResponse>> findAll() {
		return iconFileService.findAll();
	}

	@Override
	public ResponseEntity<IconFileResponse> findById(long id) {
		return iconFileService.findById(id);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return iconFileService.delete(id);
	}
}
