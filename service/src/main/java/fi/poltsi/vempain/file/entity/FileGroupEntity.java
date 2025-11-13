package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Builder
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "file_group")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class FileGroupEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	@ToString.Include
	private Long id;

	@Column(nullable = false)
	@ToString.Include
	private String path;

	@Column(name = "group_name", nullable = false)
	@ToString.Include
	private String groupName;

	@Column(name = "description", nullable = false)
	@ToString.Include
	private String description;

	@OneToMany(mappedBy = "fileGroup", fetch = FetchType.LAZY, cascade = CascadeType.MERGE)
	@Builder.Default
	private List<FileEntity> files = new ArrayList<>();

	public FileGroupResponse toResponse() {
		return FileGroupResponse.builder()
								.id(id)
								.path(path)
								.groupName(groupName)
								.description(description)
								.files(files != null ? files.stream()
															.map(FileEntity::toResponse)
															.toList() : List.of())
								.build();
	}

	public void replaceFiles(List<FileEntity> newFiles) {
		if (files == null) {
			files = new ArrayList<>();
		} else {
			files.clear();
		}
		if (newFiles != null && !newFiles.isEmpty()) {
			files.addAll(newFiles);
		}
	}
}
