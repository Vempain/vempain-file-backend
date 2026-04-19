package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.MusicFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MusicFileRepository extends JpaRepository<MusicFileEntity, Long>, JpaSpecificationExecutor<MusicFileEntity> {

	List<MusicFileEntity> findAllByOrderByArtistAscAlbumAscTrackNumberAsc();
}
