package fi.poltsi.vempain.file.repository;

public record FileGroupSummaryRow(Long id, String path, String groupName, String description, long fileCount) {
}

