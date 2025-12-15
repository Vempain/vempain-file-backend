package fi.poltsi.vempain.file.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FileGroupRepositoryImpl implements FileGroupRepositoryCustom {

	private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]+)\"|(\\S+)");

	private final EntityManager entityManager;

	@Override
	public Page<FileGroupSummaryRow> searchFileGroups(String searchTerm, boolean caseSensitive, Pageable pageable) {
		List<String> tokens = tokenize(searchTerm);

		String base = """
				FROM file_group fg
				LEFT JOIN file_group_files fgf ON fgf.file_group_id = fg.id
				LEFT JOIN files f ON f.id = fgf.file_id
				""";

		String whereClause = buildWhereClause(tokens, caseSensitive);
		String orderClause = buildOrderClause(pageable);

		String selectSql = "SELECT fg.id, fg.path, fg.group_name, fg.description, COUNT(f.id) AS file_count, fg.gallery_id "
						   + base + whereClause + " GROUP BY fg.id, fg.path, fg.group_name, fg.gallery_id, fg.description " + orderClause
						   + " OFFSET :offset LIMIT :limit";
		log.debug("FileGroup search SQL: {}", selectSql);
		Query dataQuery = entityManager.createNativeQuery(selectSql);
		bindParameters(dataQuery, tokens, caseSensitive);
		dataQuery.setParameter("offset", (int) pageable.getOffset());
		dataQuery.setParameter("limit", pageable.getPageSize());

		@SuppressWarnings("unchecked")
		List<Object[]> rawRows = dataQuery.getResultList();
		List<FileGroupSummaryRow> rows = rawRows.stream()
												.map(FileGroupRepositoryImpl::mapRow)
												.toList();

		String countSql   = "SELECT COUNT(DISTINCT fg.id) " + base + whereClause;
		log.debug("FileGroup count SQL: {}", countSql);
		Query  countQuery = entityManager.createNativeQuery(countSql);
		bindParameters(countQuery, tokens, caseSensitive);
		Number total = (Number) countQuery.getSingleResult();

		return new PageImpl<>(rows, pageable, total.longValue());
	}

	private void bindParameters(Query query, List<String> tokens, boolean caseSensitive) {
		for (int i = 0; i < tokens.size(); i++) {
			String value = caseSensitive ? tokens.get(i) : tokens.get(i)
																 .toLowerCase();
			query.setParameter("term" + i, "%" + value + "%");
		}
	}

	private String buildWhereClause(List<String> tokens, boolean caseSensitive) {
		if (tokens.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder(" WHERE ");
		for (int i = 0; i < tokens.size(); i++) {
			if (i > 0) {
				sb.append(" AND ");
			}
			sb.append('(')
			  .append(like("fg.path", i, caseSensitive))
			  .append(" OR ")
			  .append(like("fg.group_name", i, caseSensitive))
			  .append(" OR ")
			  .append(like("fg.description", i, caseSensitive))
			  .append(" OR ")
			  .append(like("f.description", i, caseSensitive))
			  .append(" OR ")
			  .append(like("f.filename", i, caseSensitive))
			  .append(" OR ")
			  .append(like("f.file_path", i, caseSensitive))
			  .append(')');
		}
		return sb.toString();
	}

	private String like(String column, int index, boolean caseSensitive) {
		return (caseSensitive ? column : "LOWER(" + column + ")") + " LIKE :term" + index;
	}

	private String buildOrderClause(Pageable pageable) {
		if (!pageable.getSort()
					 .isSorted()) {
			return " ORDER BY fg.path ASC";
		}
		StringBuilder sb    = new StringBuilder(" ORDER BY ");
		boolean       first = true;
		for (var order : pageable.getSort()) {
			if (!first) {
				sb.append(", ");
			}
			sb.append(mapSort(order.getProperty()))
			  .append(' ')
			  .append(order.getDirection()
						   .name());
			first = false;
		}
		return sb.toString();
	}

	private String mapSort(String property) {
		if (property == null) {
			return "fg.path";
		}
		return switch (property.toLowerCase()) {
			case "id" -> "fg.id";
			case "path" -> "fg.path";
			case "groupname", "group_name" -> "fg.group_name";
			case "description" -> "fg.description";
			default -> "fg.path";
		};
	}

	private List<String> tokenize(String searchTerm) {
		if (searchTerm == null || searchTerm.isBlank()) {
			return List.of();
		}
		Matcher      matcher = TOKEN_PATTERN.matcher(searchTerm);
		List<String> tokens  = new ArrayList<>();
		while (matcher.find()) {
			String quoted = matcher.group(1);
			String word   = matcher.group(2);
			tokens.add(quoted != null ? quoted : word);
		}
		return tokens;
	}

	private static FileGroupSummaryRow mapRow(Object[] tuple) {
		Long   id          = tuple[0] != null ? ((Number) tuple[0]).longValue() : null;
		String path        = tuple[1] != null ? tuple[1].toString() : null;
		String groupName   = tuple[2] != null ? tuple[2].toString() : null;
		String description = tuple[3] != null ? tuple[3].toString() : null;
		long   count       = tuple[4] != null ? ((Number) tuple[4]).longValue() : 0L;
		Long galleryId = tuple[5] != null ? ((Number) tuple[5]).longValue() : null;
		return new FileGroupSummaryRow(id, path, groupName, description, count, galleryId);
	}
}
