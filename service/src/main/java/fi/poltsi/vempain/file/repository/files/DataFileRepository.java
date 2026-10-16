package fi.poltsi.vempain.file.repository.files;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.DataFileEntity;

/**
 * Repository for DataFileEntity. Provides paging (findAll(Pageable)), lookup (findById) and deletion (deleteById).
 */
@Repository
public interface DataFileRepository extends JpaRepository<DataFileEntity, Long>, JpaSpecificationExecutor<DataFileEntity> {
    // ...existing code...
}
