package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.ImageFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageFileRepository extends JpaRepository<ImageFileEntity, Long>, JpaSpecificationExecutor<ImageFileEntity> {

	/**
	 * Find all image files in the given directory path that have GPS location data,
	 * ordered by their GPS timestamp (or original datetime as fallback).
	 */
	@Query("SELECT i FROM ImageFileEntity i WHERE i.filePath = :path AND i.gpsLocation IS NOT NULL ORDER BY COALESCE(i.gpsTimestamp, i.originalDatetime) ASC NULLS LAST")
	List<ImageFileEntity> findByFilePathWithGpsOrderedByTime(@Param("path") String path);
}
