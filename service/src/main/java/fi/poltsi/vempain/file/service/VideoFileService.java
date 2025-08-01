package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.VideoFileResponse;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.VideoFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class VideoFileService {

	private final VideoFileRepository videoFileRepository;

	@Transactional(readOnly = true)
	public ResponseEntity<List<VideoFileResponse>> findAll() {
		var fileList = videoFileRepository.findAll();
		var response = fileList.stream()
							   .map(VideoFileEntity::toResponse)
							   .toList();
		return ResponseEntity.ok(response);
	}

	public ResponseEntity<VideoFileResponse> findById(long id) {
		return ResponseEntity.ok(null);
	}

	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.ok()
							 .build();
	}
}
