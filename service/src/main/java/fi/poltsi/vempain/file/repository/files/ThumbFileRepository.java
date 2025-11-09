package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.ThumbFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for ThumbFileEntity.
 * Provides paging (findAll(Pageable)), lookup (findById) and deletion (deleteById).
 */
@Repository
public interface ThumbFileRepository extends JpaRepository<ThumbFileEntity, Long> {
	// ...existing code...
}
