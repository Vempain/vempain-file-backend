package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.ExportFileEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExportFileRepository extends JpaRepository<ExportFileEntity, Long> {
	ExportFileEntity findByOriginalDocumentId(String originalDocumentId);

	Optional<ExportFileEntity> findByFilePathAndFilename(String path, String filename);

	Optional<ExportFileEntity> findByFileId(Long fileId);

	@Query(value = """
			SELECT e.*
			FROM export_files e
			WHERE e.mimetype LIKE 'image/%'
			  AND NOT EXISTS (
			      SELECT 1
			      FROM thumb_files t
			      WHERE t.target_file_id = e.file_id
			  )
			ORDER BY RANDOM()
			""", nativeQuery = true)
	List<ExportFileEntity> findImagesMissingThumbnails(Pageable pageable);
}
