package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.AudioFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AudioFileRepository extends JpaRepository<AudioFileEntity, Long> {
	// Add custom query methods if necessary
}
