package fi.poltsi.vempain.file.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
@Entity
@Table(name = "file_group")
public class FileGroupEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String path;

	@Column(name = "group_name", nullable = false)
	private String groupName;

	@OneToMany(mappedBy = "fileGroup", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<FileEntity> files;
}
