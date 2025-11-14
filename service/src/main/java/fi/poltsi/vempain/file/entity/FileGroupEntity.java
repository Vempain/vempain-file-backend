package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

	@ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE, CascadeType.PERSIST})
	@JoinTable(
			name = "file_group_files",
			joinColumns = @JoinColumn(name = "file_group_id"),
			inverseJoinColumns = @JoinColumn(name = "file_id")
	)
	@Builder.Default
	private Set<FileEntity> files = new HashSet<>();

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
		final Set<FileEntity> newSet = newFiles == null ? Set.of() : new HashSet<>(newFiles);

		// Remove unselected files and update inverse side
		if (files != null && !files.isEmpty()) {
			var toRemove = new HashSet<>(files);
			toRemove.removeAll(newSet);
			for (FileEntity f : toRemove) {
				files.remove(f);
				if (f.getFileGroups() != null) {
					f.getFileGroups()
					 .remove(this);
				}
			}
		}

		// Add new files and update inverse side
		for (FileEntity f : newSet) {
			if (!files.contains(f)) {
				files.add(f);
				if (f.getFileGroups() != null) {
					f.getFileGroups()
					 .add(this);
				}
			}
		}
	}
}
