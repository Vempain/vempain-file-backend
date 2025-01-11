package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.ArchiveFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArchiveFileRepository extends JpaRepository<ArchiveFileEntity, Long> {
	// Add custom query methods if necessary
}
