package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.FontFileResponse;
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
@Table(name = "font_files")
public class FontFileEntity extends FileEntity {

	@Column(name = "font_family", nullable = false)
	private String fontFamily; // Example: Arial, Roboto

	@Column
	private String weight; // Example: Bold, Regular

	@Column
	private String style; // Example: Italic, Normal

	@Override
	public FontFileResponse toResponse() {
		var builder = FontFileResponse.builder()
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
		builder.fontFamily(getFontFamily())
			   .weight(getWeight())
			   .style(getStyle());
		return builder.build();
	}
}
