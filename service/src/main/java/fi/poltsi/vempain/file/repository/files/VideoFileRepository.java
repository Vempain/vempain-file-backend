package fi.poltsi.vempain.file.repository.files;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.VideoFileEntity;

@Repository
public interface VideoFileRepository extends JpaRepository<VideoFileEntity, Long>, JpaSpecificationExecutor<VideoFileEntity> {
    // Add custom query methods if necessary
}
