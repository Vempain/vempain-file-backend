package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.AudioFileResponse;
import fi.poltsi.vempain.file.service.AudioFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AudioFileController implements AudioFileAPI {

	private final AudioFileService audioFileService;

	@Override
	public ResponseEntity<List<AudioFileResponse>> findAll() {
		return audioFileService.findAll();
	}

	@Override
	public ResponseEntity<AudioFileResponse> findById(long id) {
		return audioFileService.findById(id);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return audioFileService.delete(id);
	}
}
