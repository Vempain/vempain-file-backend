package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.IconFileResponse;
import fi.poltsi.vempain.file.entity.IconFileEntity;
import fi.poltsi.vempain.file.repository.IconFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class IconFileService {

	private final IconFileRepository iconFileRepository;

	@Transactional(readOnly = true)
	public ResponseEntity<List<IconFileResponse>> findAll() {
		var fileList = iconFileRepository.findAll();
		var response = fileList.stream()
							   .map(IconFileEntity::toResponse)
							   .toList();
		return ResponseEntity.ok(response);
	}

	public ResponseEntity<IconFileResponse> findById(long id) {
		return ResponseEntity.ok(null);
	}

	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.ok()
							 .build();
	}
}
