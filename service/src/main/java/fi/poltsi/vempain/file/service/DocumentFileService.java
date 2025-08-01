package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.DocumentFileResponse;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.repository.DocumentFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class DocumentFileService {

	private final DocumentFileRepository documentFileRepository;

	@Transactional(readOnly = true)
	public ResponseEntity<List<DocumentFileResponse>> findAll() {
		var fileList = documentFileRepository.findAll();
		var response = fileList.stream()
							   .map(DocumentFileEntity::toResponse)
							   .toList();
		return ResponseEntity.ok(response);
	}

	public ResponseEntity<DocumentFileResponse> findById(long id) {
		return ResponseEntity.ok(null);
	}

	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.ok()
							 .build();
	}
}
