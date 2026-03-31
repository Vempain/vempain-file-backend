package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentFileRepository extends JpaRepository<DocumentFileEntity, Long>, JpaSpecificationExecutor<DocumentFileEntity> {
	// Add custom query methods if necessary
}
