package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.ExportFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExportFileRepository extends JpaRepository<ExportFileEntity, Long> {
	ExportFileEntity findByOriginalDocumentId(String originalDocumentId);

	Optional<ExportFileEntity> findByExportFilePathAndExportFilename(String path, String filename);
}
