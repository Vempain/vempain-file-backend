package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {
	Optional<FileEntity> findByFilePathAndFilename(String filePath, String filename);

	FileEntity findByOriginalDocumentId(String originalDocumentId);
}
