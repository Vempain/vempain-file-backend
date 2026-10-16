package fi.poltsi.vempain.file.repository.files;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.MusicFileEntity;

@Repository
public interface MusicFileRepository extends JpaRepository<MusicFileEntity, Long>, JpaSpecificationExecutor<MusicFileEntity> {

    List<MusicFileEntity> findAllByOrderByArtistAscAlbumAscTrackNumberAsc();
}
