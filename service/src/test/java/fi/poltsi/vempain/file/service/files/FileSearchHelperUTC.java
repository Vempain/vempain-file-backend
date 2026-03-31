package fi.poltsi.vempain.file.service.files;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class FileSearchHelperUTC {

	@Test
	void buildSpecification_returnsNull_forEmptySearch() {
		assertThat(FileSearchHelper.buildSpecification(null, false)).isNull();
		assertThat(FileSearchHelper.buildSpecification("", false)).isNull();
		assertThat(FileSearchHelper.buildSpecification("   ", true)).isNull();
	}

	@Test
	void buildSpecification_returnsSpecification_forNonEmptySearch() {
		var spec = FileSearchHelper.buildSpecification("holiday photo", false);
		assertThat(spec).isNotNull();
	}

	@Test
	void buildSort_mapsKnownFields() {
		assertThat(FileSearchHelper.buildSort("id", Sort.Direction.DESC)
		                           .toString()).contains("id: DESC");
		assertThat(FileSearchHelper.buildSort("filename", Sort.Direction.ASC)
		                           .toString()).contains("filename: ASC");
		assertThat(FileSearchHelper.buildSort("file_path", Sort.Direction.ASC)
		                           .toString()).contains("filePath: ASC");
		assertThat(FileSearchHelper.buildSort("filepath", Sort.Direction.DESC)
		                           .toString()).contains("filePath: DESC");
		assertThat(FileSearchHelper.buildSort("description", Sort.Direction.ASC)
		                           .toString()).contains("description: ASC");
		assertThat(FileSearchHelper.buildSort("mimetype", Sort.Direction.ASC)
		                           .toString()).contains("mimetype: ASC");
		assertThat(FileSearchHelper.buildSort("filesize", Sort.Direction.ASC)
		                           .toString()).contains("filesize: ASC");
		assertThat(FileSearchHelper.buildSort("created", Sort.Direction.ASC)
		                           .toString()).contains("created: ASC");
		assertThat(FileSearchHelper.buildSort("modified", Sort.Direction.ASC)
		                           .toString()).contains("modified: ASC");
	}

	@Test
	void buildSort_defaultsToFilenameAsc_forUnknownInputs() {
		assertThat(FileSearchHelper.buildSort(null, null)
		                           .toString()).contains("filename: ASC");
		assertThat(FileSearchHelper.buildSort("unknown", null)
		                           .toString()).contains("filename: ASC");
	}
}

