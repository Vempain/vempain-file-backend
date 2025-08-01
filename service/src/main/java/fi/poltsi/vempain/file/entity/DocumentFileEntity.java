package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.DocumentFileResponse;
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
@Table(name = "document_files")
public class DocumentFileEntity extends FileEntity {

	@Column(name = "page_count")
	private int pageCount; // Number of pages in the document

	@Column
	private String format; // Document format (e.g., PDF, DOCX)

	@Override
	public DocumentFileResponse toResponse() {
		var builder = DocumentFileResponse.builder()
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
		builder.pageCount(getPageCount())
			   .format(getFormat());
		return builder.build();
	}
}
