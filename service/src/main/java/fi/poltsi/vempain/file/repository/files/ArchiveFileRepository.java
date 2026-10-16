package fi.poltsi.vempain.file.repository.files;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.ArchiveFileEntity;

@Repository
public interface ArchiveFileRepository extends JpaRepository<ArchiveFileEntity, Long>, JpaSpecificationExecutor<ArchiveFileEntity> {
    // Add custom query methods if necessary
}
