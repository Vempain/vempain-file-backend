package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.auth.entity.AbstractVempainEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Entity
@AllArgsConstructor
@RequiredArgsConstructor
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "files")
public abstract class FileEntity extends AbstractVempainEntity {

	@ManyToOne
	@JoinColumn(name = "file_group_id", nullable = false)
	private FileGroupEntity fileGroup;

	@Column(nullable = false)
	private String filename;

	@Column(nullable = false)
	private String externalFileId;

	@Column(nullable = false)
	private String mimetype;

	@Column(nullable = false)
	private long filesize;

	@Column(nullable = false, length = 64)
	private String sha256sum;

	@Basic
	@Column(name = "original_datetime")
	protected Instant originalDatetime;

	@Basic
	@Column(name = "original_second_fraction")
	protected Integer originalSecondFraction;

	@Basic
	@Column(name = "original_document_id")
	protected String  originalDocumentId;

	@Basic
	@Column(name = "description")
	protected String  description;

	@Column(name = "file_type", nullable = false)
	private String fileType;

	@Column(name = "metadata_raw", nullable = false)
	private String metadataRaw;

	@ManyToMany
	@JoinTable(
			name = "file_tags",
			joinColumns = @JoinColumn(name = "file_id"),
			inverseJoinColumns = @JoinColumn(name = "tag_id")
	)
	@Builder.Default
	private Set<TagEntity> tags = new HashSet<>();
}
