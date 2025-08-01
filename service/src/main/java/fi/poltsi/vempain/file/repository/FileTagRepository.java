package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileTag;
import fi.poltsi.vempain.file.entity.FileTagId;
import fi.poltsi.vempain.file.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileTagRepository extends JpaRepository<FileTag, FileTagId> {
	List<FileTag> findByFile(FileEntity file);

	List<FileTag> findByTag(TagEntity tag);
}
