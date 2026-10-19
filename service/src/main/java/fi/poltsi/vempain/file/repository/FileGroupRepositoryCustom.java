package fi.poltsi.vempain.file.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FileGroupRepositoryCustom {

    Page<FileGroupSummaryRow> searchFileGroups(String searchTerm, boolean caseSensitive, Pageable pageable);

    /**
     * One row of the file group list: {@code fileCount} is the number of files in the group, {@code exportedFileCount} how many of them have an exported file
     * (the files a publish actually transfers).
     */
    record FileGroupSummaryRow(Long id, String path, String groupName, String description, long fileCount, long exportedFileCount, Long galleryId) {}
}
