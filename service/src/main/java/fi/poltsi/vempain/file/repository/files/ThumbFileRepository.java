package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.ThumbFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ThumbFileEntity.
 * Provides paging (findAll(Pageable)), lookup (findById) and deletion (deleteById).
 */
@Repository
public interface ThumbFileRepository extends JpaRepository<ThumbFileEntity, Long>, JpaSpecificationExecutor<ThumbFileEntity> {
	boolean existsByTargetFileId(Long targetFileId);

	@Query(value = "SELECT MIN(id) FROM thumb_files WHERE target_file_id = :targetFileId AND relation_type = 'thumbnail'", nativeQuery = true)
	Optional<Long> findThumbnailIdByTargetFileId(@Param("targetFileId") Long targetFileId);

	@Query(value = """
			SELECT target_file_id, MIN(id)
			FROM thumb_files
			WHERE target_file_id IN (:targetFileIds)
			  AND relation_type = 'thumbnail'
			GROUP BY target_file_id
			""", nativeQuery = true)
	List<Object[]> findThumbnailIdsByTargetFileIds(@Param("targetFileIds") Collection<Long> targetFileIds);
}
