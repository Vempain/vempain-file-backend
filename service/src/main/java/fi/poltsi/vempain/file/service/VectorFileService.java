package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.VectorFileResponse;
import fi.poltsi.vempain.file.entity.VectorFileEntity;
import fi.poltsi.vempain.file.repository.VectorFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class VectorFileService {

	private final VectorFileRepository vectorFileRepository;

	@Transactional(readOnly = true)
	public ResponseEntity<List<VectorFileResponse>> findAll() {
		var fileList = vectorFileRepository.findAll();
		var response = fileList.stream()
							   .map(VectorFileEntity::toResponse)
							   .toList();
		return ResponseEntity.ok(response);
	}

	public ResponseEntity<VectorFileResponse> findById(long id) {
		return ResponseEntity.ok(null);
	}

	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.ok()
							 .build();
	}
}
