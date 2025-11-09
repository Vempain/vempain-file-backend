package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.DataFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for DataFileEntity.
 * Provides paging (findAll(Pageable)), lookup (findById) and deletion (deleteById).
 */
@Repository
public interface DataFileRepository extends JpaRepository<DataFileEntity, Long> {
	// ...existing code...
}
