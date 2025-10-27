package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.IconFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IconFileRepository extends JpaRepository<IconFileEntity, Long> {
	// Add custom query methods if necessary
}
