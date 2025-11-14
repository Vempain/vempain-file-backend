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
					SELECT fg.id AS id, fg.path AS path, fg.groupName AS groupName, fg.description AS description, COUNT(f) AS fileCount
					FROM FileGroupEntity fg
					LEFT JOIN fg.files f
					GROUP BY fg.id, fg.path, fg.groupName
					""",
			countQuery = "SELECT COUNT(fg) FROM FileGroupEntity fg"
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
