package fi.poltsi.vempain.file.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FileGroupRepositoryCustom {
	Page<FileGroupSummaryRow> searchFileGroups(String searchTerm, boolean caseSensitive, Pageable pageable);

	record FileGroupSummaryRow(Long id, String path, String groupName, String description, long fileCount) {
	}
}
