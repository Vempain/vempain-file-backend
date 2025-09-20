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
									  .id(this.id)
									  .locked(this.locked)
									  .creator(this.creator)
									  .created(this.created)
									  .modifier(this.modifier)
									  .modified(this.modified)
									  .filename(getFilename())
									  .filePath(getFilePath())
									  .externalFileId(getExternalFileId())
									  .mimetype(getMimetype())
									  .filesize(getFilesize())
									  .sha256sum(getSha256sum())
									  .originalDatetime(getOriginalDatetime())
									  .originalSecondFraction(getOriginalSecondFraction())
									  .originalDocumentId(getOriginalDocumentId())
									  .gpsTimestamp(getGpsTimestamp())
									  .gpsLocationId(getGpsLocationId())
									  .rightsHolder(getRightsHolder())
									  .rightsTerms(getRightsTerms())
									  .rightsUrl(getRightsUrl())
									  .creatorCountry(getCreatorCountry())
									  .creatorEmail(getCreatorEmail())
									  .creatorUrl(getCreatorUrl())
									  .creatorName(getCreatorName())
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
