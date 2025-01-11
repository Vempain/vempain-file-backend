package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.MetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetadataRepository extends JpaRepository<MetadataEntity, Long> {
	List<MetadataEntity> findByFile(FileEntity file);
}
