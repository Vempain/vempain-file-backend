package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.ArchiveFileResponse;
import fi.poltsi.vempain.file.service.ArchiveFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ArchiveFileController implements ArchiveFileAPI {

	private final ArchiveFileService archiveFileService;

	@Override
	public ResponseEntity<List<ArchiveFileResponse>> findAll() {
		return archiveFileService.findAll();
	}

	@Override
	public ResponseEntity<ArchiveFileResponse> findById(long id) {
		return archiveFileService.findById(id);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return archiveFileService.delete(id);
	}
}
