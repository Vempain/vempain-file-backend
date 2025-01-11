package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.ImageFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageFileRepository extends JpaRepository<ImageFileEntity, Long> {
	// Add custom query methods if necessary
}
