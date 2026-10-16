package fi.poltsi.vempain.file.repository.files;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.IconFileEntity;

@Repository
public interface IconFileRepository extends JpaRepository<IconFileEntity, Long>, JpaSpecificationExecutor<IconFileEntity> {
    // Add custom query methods if necessary
}
