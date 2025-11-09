package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.ExecutableFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for ExecutableFileEntity.
 * Provides paging (findAll(Pageable)), lookup (findById) and deletion (deleteById).
 */
@Repository
public interface ExecutableFileRepository extends JpaRepository<ExecutableFileEntity, Long> {
	// ...existing code...
}
