package fi.poltsi.vempain.file.repository.files;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.AudioFileEntity;

@Repository
public interface AudioFileRepository extends JpaRepository<AudioFileEntity, Long>, JpaSpecificationExecutor<AudioFileEntity> {
    // Add custom query methods if necessary
}
