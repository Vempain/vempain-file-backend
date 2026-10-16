package fi.poltsi.vempain.file.repository.files;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.InteractiveFileEntity;

/**
 * Repository for InteractiveFileEntity. Provides paging (findAll(Pageable)), lookup (findById) and deletion (deleteById).
 */
@Repository
public interface InteractiveFileRepository extends JpaRepository<InteractiveFileEntity, Long>, JpaSpecificationExecutor<InteractiveFileEntity> {
    // ...existing code...
}
