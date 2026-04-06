package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.VideoFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoFileRepository extends JpaRepository<VideoFileEntity, Long>, JpaSpecificationExecutor<VideoFileEntity> {
	// Add custom query methods if necessary
}
