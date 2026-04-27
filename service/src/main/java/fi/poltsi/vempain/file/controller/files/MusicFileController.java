package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.MusicFileResponse;
import fi.poltsi.vempain.file.rest.files.MusicFileAPI;
import fi.poltsi.vempain.file.service.files.MusicFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MusicFileController implements MusicFileAPI {

	private final MusicFileService musicFileService;

	@Override
	public ResponseEntity<PagedResponse<MusicFileResponse>> findAll(PagedRequest pagedRequest) {
		return ResponseEntity.ok(musicFileService.findAll(pagedRequest));
	}

	@Override
	public ResponseEntity<MusicFileResponse> findById(long id) {
		var response = musicFileService.findById(id);

		if (response == null) {
			return ResponseEntity.notFound()
								 .build();
		}

		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.status(musicFileService.delete(id))
							 .build();
	}
}
