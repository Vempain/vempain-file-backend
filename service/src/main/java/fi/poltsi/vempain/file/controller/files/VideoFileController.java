package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.VideoFileResponse;
import fi.poltsi.vempain.file.rest.files.VideoFileAPI;
import fi.poltsi.vempain.file.service.files.VideoFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VideoFileController implements VideoFileAPI {

	private final VideoFileService videoFileService;

	@Override
	public ResponseEntity<PagedResponse<VideoFileResponse>> findAll(int page, int size) {
		return ResponseEntity.ok(videoFileService.findAll(page, size));
	}

	@Override
	public ResponseEntity<VideoFileResponse> findById(long id) {
		var response = videoFileService.findById(id);

		if (response == null) {
			return ResponseEntity.notFound()
								 .build();
		}

		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.status(videoFileService.delete(id))
							 .build();
	}
}
