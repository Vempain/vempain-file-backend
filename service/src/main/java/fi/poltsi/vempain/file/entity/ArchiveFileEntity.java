package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.ArchiveFileResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.stream.Collectors;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "archive_files")
public class ArchiveFileEntity extends FileEntity {

	@Column(name = "compression_method", nullable = false)
	private String compressionMethod; // E.g., gzip, zip

	@Column(name = "uncompressed_size", nullable = false)
	private Long uncompressedSize; // Size of the archive when decompressed

	@Column(name = "content_count", nullable = false)
	private int contentCount; // Number of items inside the archive

	@Column(name = "is_encrypted", nullable = false)
	private Boolean isEncrypted; // True if password-protected

	@Override
	public ArchiveFileResponse toResponse() {
		var builder = ArchiveFileResponse.builder()
										 .filename(getFilename())
										 .externalFileId(getExternalFileId())
										 .mimetype(getMimetype())
										 .filesize(getFilesize())
										 .sha256sum(getSha256sum())
										 .originalDatetime(getOriginalDatetime())
										 .originalSecondFraction(getOriginalSecondFraction())
										 .originalDocumentId(getOriginalDocumentId())
										 .description(getDescription())
										 .fileType(getFileType())
										 .metadataRaw(getMetadataRaw())
										 .tags(getTags().stream()
														.map(TagEntity::getTagName)
														.collect(Collectors.toList()));
		builder.compressionMethod(getCompressionMethod())
			   .uncompressedSize(getUncompressedSize())
			   .contentCount(getContentCount())
			   .isEncrypted(getIsEncrypted());
		return builder.build();
	}
}
