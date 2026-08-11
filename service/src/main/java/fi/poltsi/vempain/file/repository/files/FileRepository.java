package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long>, JpaSpecificationExecutor<FileEntity> {
	Optional<FileEntity> findByFilePathAndFilename(String filePath, String filename);

	FileEntity findByOriginalDocumentId(String originalDocumentId);

	@Query(value = "SELECT file_type AS fileType, COUNT(*) AS fileCount FROM files GROUP BY file_type", nativeQuery = true)
	List<FileTypeCountProjection> countFilesByType();

	@Query(value = """
			SELECT file_type AS fileType, EXTRACT(YEAR FROM original_datetime)::int AS creationYear, COUNT(*) AS fileCount
			FROM files
			WHERE original_datetime IS NOT NULL
			GROUP BY file_type, EXTRACT(YEAR FROM original_datetime)
			""", nativeQuery = true)
	List<FileTypeYearCountProjection> countFilesByTypeAndCreationYear();

	@Query(value = "SELECT COUNT(DISTINCT gps_location_id) FROM files WHERE gps_location_id IS NOT NULL", nativeQuery = true)
	long countFilesWithGpsLocations();

	@Query(value = "SELECT COUNT(DISTINCT file_id) FROM file_tags", nativeQuery = true)
	long countFilesWithTags();

	@Query(value = "SELECT COUNT(*) FROM files", nativeQuery = true)
	long countTotalFiles();

	@Query(value = "SELECT COALESCE(SUM(filesize), 0) FROM files", nativeQuery = true)
	long sumFileSizes();

	@Query(value = "SELECT COALESCE(MAX(filesize), 0) FROM files", nativeQuery = true)
	long maxFileSize();

	@Query(value = "SELECT COALESCE(AVG(filesize), 0) FROM files", nativeQuery = true)
	BigDecimal averageFileSize();

	@Query(value = """
			SELECT file_type AS fileType, MAX(filesize) AS largestFileSize, AVG(filesize) AS averageFileSize
			FROM files
			GROUP BY file_type
			""", nativeQuery = true)
	List<FileTypeSizeProjection> summarizeFileSizesByType();

	@Query(value = "SELECT COUNT(fgf.file_id) FROM file_group fg LEFT JOIN file_group_files fgf ON fgf.file_group_id = fg.id GROUP BY fg.id",
	       nativeQuery = true)
	List<Long> findFileGroupSizes();
}
