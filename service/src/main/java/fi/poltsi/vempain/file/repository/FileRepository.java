package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {
	FileEntity findByFilename(String filename);
}
