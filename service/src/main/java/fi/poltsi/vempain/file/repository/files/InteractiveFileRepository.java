package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.InteractiveFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for InteractiveFileEntity.
 * Provides paging (findAll(Pageable)), lookup (findById) and deletion (deleteById).
 */
@Repository
public interface InteractiveFileRepository extends JpaRepository<InteractiveFileEntity, Long> {
	// ...existing code...
}
