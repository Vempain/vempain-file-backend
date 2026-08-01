package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.ImageFileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Custom repository interface for ImageFileEntity to support JOIN FETCH queries
 * that avoid N+1 query problems with lazy-loaded relationships.
 */
public interface ImageFileRepositoryCustom {

	/**
	 * Find all image files with eager-loaded relationships (tags, gpsLocation).
	 * Uses JOIN FETCH to load related entities in a single query, avoiding N+1 query problems.
	 *
	 * @param spec     optional JPA Specification for filtering
	 * @param pageable pagination and sorting information
	 * @return Page of ImageFileEntity with all relationships loaded
	 */
	Page<ImageFileEntity> findAllWithRelationships(Specification<ImageFileEntity> spec, Pageable pageable);
}
