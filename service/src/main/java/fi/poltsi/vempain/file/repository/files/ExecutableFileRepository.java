package fi.poltsi.vempain.file.repository.files;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.ExecutableFileEntity;

/**
 * Repository for ExecutableFileEntity. Provides paging (findAll(Pageable)), lookup (findById) and deletion (deleteById).
 */
@Repository
public interface ExecutableFileRepository extends JpaRepository<ExecutableFileEntity, Long>, JpaSpecificationExecutor<ExecutableFileEntity> {
    // ...existing code...
}
