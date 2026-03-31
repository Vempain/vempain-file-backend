package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.file.entity.FileEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared helper for building JPA Specifications and Sort objects from PagedRequest parameters
 * across all typed file services (ArchiveFileService, ImageFileService, etc.).
 */
public final class FileSearchHelper {

	private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]+)\"|(\\S+)");

	private FileSearchHelper() {
	}

	/**
	 * Builds a JPA Specification that performs a multi-token LIKE search across
	 * filename, filePath, description, and mimetype fields inherited from FileEntity.
	 * Each token must match at least one of those fields (AND between tokens, OR between fields).
	 *
	 * @param search        raw search string (may be null or blank)
	 * @param caseSensitive whether the search should be case-sensitive
	 * @return Specification, or null if search is empty
	 */
	public static <T extends FileEntity> Specification<T> buildSpecification(String search, boolean caseSensitive) {
		List<String> tokens = tokenize(search);
		if (tokens.isEmpty()) {
			return null;
		}

		return (root, query, cb) -> {
			List<Predicate> andPredicates = new ArrayList<>();
			for (String token : tokens) {
				String          pattern      = "%" + (caseSensitive ? token : token.toLowerCase()) + "%";
				List<Predicate> orPredicates = new ArrayList<>();
				for (String field : List.of("filename", "filePath", "description", "mimetype")) {
					var path = root.<String>get(field);
					if (caseSensitive) {
						orPredicates.add(cb.like(path, pattern));
					} else {
						orPredicates.add(cb.like(cb.lower(path), pattern));
					}
				}
				andPredicates.add(cb.or(orPredicates.toArray(new Predicate[0])));
			}
			return cb.and(andPredicates.toArray(new Predicate[0]));
		};
	}

	/**
	 * Builds a Sort from sortBy / direction parameters.
	 * Supported sort fields: id, filename, filepath/file_path, description, mimetype, filesize, created, modified.
	 * Defaults to filename ASC.
	 */
	public static Sort buildSort(String sortBy, Sort.Direction direction) {
		String property = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
			case "id" -> "id";
			case "filename" -> "filename";
			case "filepath", "file_path" -> "filePath";
			case "description" -> "description";
			case "mimetype" -> "mimetype";
			case "filesize" -> "filesize";
			case "created" -> "created";
			case "modified" -> "modified";
			default -> "filename";
		};
		Sort.Direction dir = direction == null ? Sort.Direction.ASC : direction;
		return Sort.by(dir, property);
	}

	private static List<String> tokenize(String search) {
		if (search == null || search.isBlank()) {
			return List.of();
		}
		Matcher      matcher = TOKEN_PATTERN.matcher(search);
		List<String> tokens  = new ArrayList<>();
		while (matcher.find()) {
			String quoted = matcher.group(1);
			String word   = matcher.group(2);
			tokens.add(quoted != null ? quoted : word);
		}
		return tokens;
	}
}

