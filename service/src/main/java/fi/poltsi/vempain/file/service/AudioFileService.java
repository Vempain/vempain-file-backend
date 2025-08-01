package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.AudioFileResponse;
import fi.poltsi.vempain.file.entity.AudioFileEntity;
import fi.poltsi.vempain.file.repository.AudioFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class AudioFileService {

	private final AudioFileRepository audioFileRepository;

	@Transactional(readOnly = true)
	public ResponseEntity<List<AudioFileResponse>> findAll() {
		var fileList = audioFileRepository.findAll();
		var response = fileList.stream()
							   .map(AudioFileEntity::toResponse)
							   .toList();
		return ResponseEntity.ok(response);
	}

	public ResponseEntity<AudioFileResponse> findById(long id) {
		return ResponseEntity.ok(null);
	}

	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.ok()
							 .build();
	}
}
