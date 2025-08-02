package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.auth.entity.AbstractVempainEntity;
import fi.poltsi.vempain.file.api.response.FileResponse;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

	@Basic
	@Column(name = "original_datetime")
	protected Instant         originalDatetime;
	@Basic
	@Column(name = "original_second_fraction")
	protected Integer         originalSecondFraction;
	@Basic
	@Column(name = "original_document_id")
	protected String          originalDocumentId;
	@Basic
	@Column(name = "description")
	protected String          description;
	@ManyToOne
	@JoinColumn(name = "file_group_id", nullable = false)
	private   FileGroupEntity fileGroup;
	@Column(name = "filename", nullable = false)
	private   String          filename;
	@Column(name = "file_path", nullable = false)
	private String filePath;
	@Column(name = "external_file_id", nullable = false)
	private   String          externalFileId;
	@Column(name = "mimetype", nullable = false)
	private   String          mimetype;
	@Column(name = "filesize", nullable = false)
	private   long            filesize;
	@Column(name = "sha256sum", nullable = false, length = 64)
	private   String          sha256sum;
	@Column(name = "file_type", nullable = false)
	private   String          fileType;

	@EqualsAndHashCode.Exclude
	@Column(name = "metadata_raw", nullable = false)
	private String metadataRaw;

	@EqualsAndHashCode.Exclude
	@ManyToMany
	@JoinTable(
			name = "file_tags",
			joinColumns = @JoinColumn(name = "file_id"),
			inverseJoinColumns = @JoinColumn(name = "tag_id")
	)
	@Builder.Default
	private Set<TagEntity> tags = new HashSet<>();

	public FileResponse toResponse() {
		List<String> tagNames = this.tags.stream()
										 .map(TagEntity::getTagName)
										 .collect(Collectors.toList());
		return FileResponse.builder()
						   .id(this.id)
						   .filename(this.filename)
						   .filePath(this.filePath)
						   .externalFileId(this.externalFileId)
						   .mimetype(this.mimetype)
						   .filesize(this.filesize)
						   .sha256sum(this.sha256sum)
						   .originalDatetime(this.originalDatetime)
						   .originalSecondFraction(this.originalSecondFraction)
						   .originalDocumentId(this.originalDocumentId)
						   .description(this.description)
						   .fileType(this.fileType)
						   .metadataRaw(this.metadataRaw)
						   .tags(tagNames)
						   .build();
	}
}
