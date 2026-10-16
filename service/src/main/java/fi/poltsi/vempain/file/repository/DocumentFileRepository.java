package fi.poltsi.vempain.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.DocumentFileEntity;

@Repository
public interface DocumentFileRepository extends JpaRepository<DocumentFileEntity, Long>, JpaSpecificationExecutor<DocumentFileEntity> {
    // Add custom query methods if necessary
}
