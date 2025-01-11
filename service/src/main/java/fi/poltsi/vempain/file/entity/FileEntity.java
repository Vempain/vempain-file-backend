package fi.poltsi.vempain.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@SuperBuilder
@Data
@Entity
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "files")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class FileEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "file_group_id", nullable = false)
	private FileGroupEntity fileGroup;

	@Column(nullable = false)
	private String filename;

	@Column(nullable = false)
	private String mimetype;

	@Column(nullable = false)
	private long filesize;

	@Column(nullable = false, length = 64)
	private String sha256sum;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "file_type", nullable = false)
	private String fileType;

	@ManyToMany
	@JoinTable(
			name = "file_tags",
			joinColumns = @JoinColumn(name = "file_id"),
			inverseJoinColumns = @JoinColumn(name = "tag_id")
	)
	@Builder.Default
	private Set<TagEntity> tags = new HashSet<>();
}
