package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.VectorFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface VectorFileRepository extends JpaRepository<VectorFileEntity, Long>, JpaSpecificationExecutor<VectorFileEntity> {
	// Add custom query methods if necessary
}
