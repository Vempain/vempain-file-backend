package fi.poltsi.vempain.file.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileTag;
import fi.poltsi.vempain.file.entity.FileTagId;
import fi.poltsi.vempain.file.entity.TagEntity;

@Repository
public interface FileTagRepository extends JpaRepository<FileTag, FileTagId> {

    List<FileTag> findByFile(FileEntity file);

    List<FileTag> findByTag(TagEntity tag);

    List<FileTag> findByFileId(long fileId);
}
