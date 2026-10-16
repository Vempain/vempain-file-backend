package fi.poltsi.vempain.file.repository.files;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.FontFileEntity;

@Repository
public interface FontFileRepository extends JpaRepository<FontFileEntity, Long>, JpaSpecificationExecutor<FontFileEntity> {
    // Add custom query methods if necessary
}
