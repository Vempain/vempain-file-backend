package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.VideoFileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class VideoFileService {

	public ResponseEntity<List<VideoFileResponse>> findAll() {
		return ResponseEntity.ok(Collections.emptyList());
	}

	public ResponseEntity<VideoFileResponse> findById(long id) {
		return ResponseEntity.ok(null);
	}

	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.ok()
							 .build();
	}
}
