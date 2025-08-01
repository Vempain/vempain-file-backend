package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.ArchiveFileResponse;
import fi.poltsi.vempain.file.entity.ArchiveFileEntity;
import fi.poltsi.vempain.file.repository.ArchiveFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ArchiveFileService {

	private final ArchiveFileRepository archiveFileRepository;

	@Transactional(readOnly = true)
	public ResponseEntity<List<ArchiveFileResponse>> findAll() {
		var fileList = archiveFileRepository.findAll();
		var response = fileList.stream()
							   .map(ArchiveFileEntity::toResponse)
							   .toList();
		return ResponseEntity.ok(response);
	}

	public ResponseEntity<ArchiveFileResponse> findById(long id) {
		return ResponseEntity.ok(null);
	}

	public ResponseEntity<Void> delete(long id) {
		return ResponseEntity.ok()
							 .build();
	}
}
