package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.AudioFileResponse;
import fi.poltsi.vempain.file.rest.files.AudioFileAPI;
import fi.poltsi.vempain.file.service.files.AudioFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AudioFileController implements AudioFileAPI {

	private final AudioFileService audioFileService;

	@Override
	public ResponseEntity<PagedResponse<AudioFileResponse>> findAll(int page, int size) {
		return ResponseEntity.ok(audioFileService.findAll(page, size));
	}

	@Override
	public ResponseEntity<AudioFileResponse> findById(long id) {
		var response = audioFileService.findById(id);

		if (response == null) {
			return ResponseEntity.notFound()
								 .build();
		}

		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.status(audioFileService.delete(id))
							 .build();

	}
}
