package fi.poltsi.vempain.file.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.FileThumbnailEntity;

@Repository
public interface FileThumbnailRepository extends JpaRepository<FileThumbnailEntity, Long> {

    Optional<FileThumbnailEntity> findByFileId(Long fileId);

    boolean existsByFileId(Long fileId);

    List<FileThumbnailEntity> findByFileIdIn(Collection<Long> fileIds);

    void deleteByFileId(Long fileId);
}
