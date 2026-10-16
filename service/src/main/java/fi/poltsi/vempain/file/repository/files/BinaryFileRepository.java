package fi.poltsi.vempain.file.repository.files;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.BinaryFileEntity;

/**
 * Repository for BinaryFileEntity. Provides paging (findAll(Pageable)), lookup (findById) and deletion (deleteById).
 */
@Repository
public interface BinaryFileRepository extends JpaRepository<BinaryFileEntity, Long>, JpaSpecificationExecutor<BinaryFileEntity> {
    // ...existing code...
}
