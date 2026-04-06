package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.ImageFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageFileRepository extends JpaRepository<ImageFileEntity, Long>, JpaSpecificationExecutor<ImageFileEntity> {
	// Add custom query methods if necessary
}
