package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.ImageFileResponse;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.repository.ImageFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ImageFileService {

	private final ImageFileRepository imageFileRepository;

	@Transactional(readOnly = true)
	public ResponseEntity<List<ImageFileResponse>> findAll() {
		var fileList = imageFileRepository.findAll();
		var response = fileList.stream()
							   .map(ImageFileEntity::toResponse)
							   .toList();
		return ResponseEntity.ok(response);
	}

	public ResponseEntity<ImageFileResponse> findById(long id) {
		return ResponseEntity.ok(null);
	}

	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.ok()
							 .build();
	}
}
