package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.FontFileResponse;
import fi.poltsi.vempain.file.entity.FontFileEntity;
import fi.poltsi.vempain.file.repository.FontFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class FontFileService {

	private final FontFileRepository fontFileRepository;

	@Transactional(readOnly = true)
	public ResponseEntity<List<FontFileResponse>> findAll() {
		var fileList = fontFileRepository.findAll();
		var response = fileList.stream()
							   .map(FontFileEntity::toResponse)
							   .toList();
		return ResponseEntity.ok(response);
	}

	public ResponseEntity<FontFileResponse> findById(long id) {
		return ResponseEntity.ok(null);
	}

	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.ok()
							 .build();
	}
}
