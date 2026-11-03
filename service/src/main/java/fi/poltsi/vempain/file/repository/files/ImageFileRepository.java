package fi.poltsi.vempain.file.repository.files;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.ImageFileEntity;

@Repository
public interface ImageFileRepository extends JpaRepository<ImageFileEntity, Long>, JpaSpecificationExecutor<ImageFileEntity>, ImageFileRepositoryCustom {

    /**
     * The GPS-tagged images of a directory that may appear in a published GPS time series: only images that have an export file and were published to the admin
     * backend ({@code site_file_published}); see {@link #findByFileGroupIdWithGpsOrderedByTime}.
     */
    @Query("""
            SELECT i FROM ImageFileEntity i
            JOIN ExportFileEntity e ON e.file = i
            WHERE i.filePath = :path AND i.gpsLocation IS NOT NULL AND i.siteFilePublished = TRUE
            ORDER BY COALESCE(i.gpsTimestamp, i.originalDatetime) ASC NULLS LAST
            """)
    List<ImageFileEntity> findByFilePathWithGpsOrderedByTime(@Param("path") String path);

    /**
     * The GPS-tagged images of a file group that may appear in a published GPS time series: only images that have an export file and were published to the
     * admin backend ({@code site_file_published}), so that the coordinates of originals that never left this service are never released. Ordered by their GPS
     * timestamp (or original datetime as fallback). The location guards are applied by {@code DataService} on top of this.
     */
    @Query("""
            SELECT i FROM ImageFileEntity i
            JOIN i.fileGroups fg
            JOIN ExportFileEntity e ON e.file = i
            WHERE fg.id = :fileGroupId AND i.gpsLocation IS NOT NULL AND i.siteFilePublished = TRUE
            ORDER BY COALESCE(i.gpsTimestamp, i.originalDatetime) ASC NULLS LAST
            """)
    List<ImageFileEntity> findByFileGroupIdWithGpsOrderedByTime(@Param("fileGroupId") Long fileGroupId);
}
