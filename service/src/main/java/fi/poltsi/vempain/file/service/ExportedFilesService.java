package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportedFilesService {
	private final ExportFileRepository exportFileRepository;

	/**
	 * Search for an exported file by its path and filename
	 */

	public boolean existsByPathAndFilename(String path, String filename) {
		log.debug("Checking if exported file exists at path: {}, filename: {}", path, filename);
		return exportFileRepository.findByFilePathAndFilename(path, filename)
								   .isPresent();
	}

	public boolean existsByOriginalDocumentId(String originalDocumentId) {
		log.debug("Checking if exported file exists with original document ID: {}", originalDocumentId);
		return exportFileRepository.findByOriginalDocumentId(originalDocumentId) != null;
	}

	@Transactional
	public ExportFileEntity save(ExportFileEntity exportFileEntity) {
		return exportFileRepository.save(exportFileEntity);
	}
}
