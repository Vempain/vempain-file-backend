package fi.poltsi.vempain.file.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Native queries over the {@code metadata} table that JPA derived queries cannot express (grouping with counts).
 */
public interface MetadataRepositoryCustom {

	/**
	 * Distinct group, key and value combinations with the number of files carrying each one. Every filter is an optional
	 * contains-match on its column; sorting accepts {@code metadata_group}, {@code metadata_key}, {@code metadata_value} and
	 * {@code file_count}.
	 */
	Page<MetadataSummaryRow> searchSummaries(String groupFilter, String keyFilter, String valueFilter, boolean caseSensitive, Pageable pageable);

	record MetadataSummaryRow(String metadataGroup, String metadataKey, String metadataValue, long fileCount) {
	}
}
