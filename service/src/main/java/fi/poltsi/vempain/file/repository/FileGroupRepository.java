package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.FileGroupEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileGroupRepository extends JpaRepository<FileGroupEntity, Long> {
	long countById(Long fileGroupId);

	Optional<FileGroupEntity> findByPathAndGroupName(String relativeDirectory, String groupName);

	// Paged query that groups by file group and counts files without loading the collection
	@Query(
			value = """
					SELECT fg.id AS id,
						   fg.path AS path,
						   fg.group_name AS groupName,
						   fg.description AS description,
						   COUNT(fgf.file_id) AS fileCount
					FROM file_group fg
					LEFT JOIN file_group_files fgf ON fgf.file_group_id = fg.id
					GROUP BY fg.id, fg.path, fg.group_name, fg.description
					""",
			countQuery = "SELECT COUNT(*) FROM file_group",
			nativeQuery = true
	)
	Page<FileGroupCountProjection> findAllWithFileCounts(Pageable pageable);

	// Projection for lightweight listing with counts
	interface FileGroupCountProjection {
		Long getId();

		String getPath();

		String getGroupName();

		String getDescription();

		long getFileCount();
	}
}
