package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.BinaryFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for BinaryFileEntity.
 * Provides paging (findAll(Pageable)), lookup (findById) and deletion (deleteById).
 */
@Repository
public interface BinaryFileRepository extends JpaRepository<BinaryFileEntity, Long> {
	// ...existing code...
}
