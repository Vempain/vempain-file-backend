package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.VideoFileResponse;
import fi.poltsi.vempain.file.service.VideoFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VideoFileController implements VideoFileAPI {

	private final VideoFileService videoFileService;

	@Override
	public ResponseEntity<List<VideoFileResponse>> findAll() {
		return videoFileService.findAll();
	}

	@Override
	public ResponseEntity<VideoFileResponse> findById(long id) {
		return videoFileService.findById(id);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return videoFileService.delete(id);
	}
}
