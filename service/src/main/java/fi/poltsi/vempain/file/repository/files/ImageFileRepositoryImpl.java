package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.file.entity.ImageFileEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Custom repository implementation for ImageFileEntity.
 * <p>
 * Loads a page of image files together with their lazy relationships (tags, gpsLocation) while
 * keeping the pagination on the database side.
 * <p>
 * A naive {@code JOIN FETCH} on the {@code tags} collection combined with
 * {@code setFirstResult}/{@code setMaxResults} forces Hibernate to read the <em>entire</em> result
 * set and paginate it in memory (Hibernate logs {@code HHH90003004: firstResult/maxResults specified
 * with collection fetch; applying in memory}). For a large {@code files}/{@code image_files} table
 * (which also carries a potentially large {@code metadata_raw} text column) this makes every page
 * request scan the whole table, so a single page can take several seconds.
 * <p>
 * To avoid that, the query is split into two phases:
 * <ol>
 *     <li>Select only the primary keys of the requested page, applying the filter, sort and
 *     {@code LIMIT}/{@code OFFSET} on the database (no collection fetch, so real SQL pagination).</li>
 *     <li>Fetch the full entities for those ids with their relationships eagerly loaded (no
 *     {@code LIMIT}, so no in-memory pagination), preserving the requested order.</li>
 * </ol>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ImageFileRepositoryImpl implements ImageFileRepositoryCustom {

	private final EntityManager entityManager;

	@Override
	public Page<ImageFileEntity> findAllWithRelationships(Specification<ImageFileEntity> spec, Pageable pageable) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();

		// Phase 1: fetch the ids of the requested page. No collection fetch here, so LIMIT/OFFSET
		// are applied by the database instead of loading the whole table into memory.
		CriteriaQuery<Long>   idQuery = cb.createQuery(Long.class);
		Root<ImageFileEntity> idRoot  = idQuery.from(ImageFileEntity.class);
		idQuery.select(idRoot.get("id"));
		applySpecification(spec, idQuery, idRoot, cb);
		if (pageable.getSort()
		            .isSorted()) {
			idQuery.orderBy(toOrders(pageable.getSort(), idRoot, cb));
		}

		TypedQuery<Long> idTypedQuery = entityManager.createQuery(idQuery);
		idTypedQuery.setFirstResult((int) pageable.getOffset());
		idTypedQuery.setMaxResults(pageable.getPageSize());
		List<Long> ids = idTypedQuery.getResultList();

		// Total count for pagination metadata.
		CriteriaQuery<Long>   countQuery = cb.createQuery(Long.class);
		Root<ImageFileEntity> countRoot  = countQuery.from(ImageFileEntity.class);
		applySpecification(spec, countQuery, countRoot, cb);
		countQuery.select(cb.count(countRoot));
		Long total = entityManager.createQuery(countQuery)
		                          .getSingleResult();

		if (ids.isEmpty()) {
			return new PageImpl<>(List.of(), pageable, total);
		}

		// Phase 2: load only this page's entities with their relationships eagerly fetched.
		// There is no LIMIT/OFFSET here (only the page's ids), so Hibernate does not paginate in memory.
		CriteriaQuery<ImageFileEntity> query = cb.createQuery(ImageFileEntity.class);
		Root<ImageFileEntity>          root  = query.from(ImageFileEntity.class);
		root.fetch("tags", JoinType.LEFT);
		root.fetch("gpsLocation", JoinType.LEFT);
		query.select(root)
		     .distinct(true)
		     .where(root.get("id")
		                .in(ids));
		if (pageable.getSort()
		            .isSorted()) {
			query.orderBy(toOrders(pageable.getSort(), root, cb));
		}

		List<ImageFileEntity> content = entityManager.createQuery(query)
		                                             .getResultList();

		return new PageImpl<>(content, pageable, total);
	}

	private void applySpecification(Specification<ImageFileEntity> spec, CriteriaQuery<?> query, Root<ImageFileEntity> root,
	                                CriteriaBuilder cb) {
		if (spec == null) {
			return;
		}
		Predicate predicate = spec.toPredicate(root, query, cb);
		if (predicate != null) {
			query.where(predicate);
		}
	}

	private Order[] toOrders(Sort sort, Root<ImageFileEntity> root, CriteriaBuilder cb) {
		return sort.stream()
		           .map(order -> order.isAscending()
		                         ? cb.asc(root.get(order.getProperty()))
		                         : cb.desc(root.get(order.getProperty())))
		           .toArray(Order[]::new);
	}
}
