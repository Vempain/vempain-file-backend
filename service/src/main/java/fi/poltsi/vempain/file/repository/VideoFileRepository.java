package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.VideoFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoFileRepository extends JpaRepository<VideoFileEntity, Long> {
	// Add custom query methods if necessary
}
