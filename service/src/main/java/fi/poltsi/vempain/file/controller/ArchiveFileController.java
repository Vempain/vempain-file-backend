package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.api.response.ArchiveFileResponse;
import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.rest.ArchiveFileAPI;
import fi.poltsi.vempain.file.service.ArchiveFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ArchiveFileController implements ArchiveFileAPI {

	private final ArchiveFileService archiveFileService;

	@Override
	public ResponseEntity<PagedResponse<ArchiveFileResponse>> findAll(int page, int size) {
		return ResponseEntity.ok(archiveFileService.findAll(page, size));
	}

	@Override
	public ResponseEntity<ArchiveFileResponse> findById(long id) {
		var response = archiveFileService.findById(id);

		if (response == null) {
			return ResponseEntity.notFound()
								 .build();
		}

		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.status(archiveFileService.delete(id))
							 .build();
	}
}
