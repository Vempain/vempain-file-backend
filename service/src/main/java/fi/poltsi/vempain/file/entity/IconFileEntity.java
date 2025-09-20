package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.IconFileResponse;
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
@Table(name = "icon_files")
public class IconFileEntity extends FileEntity {

	@Column(name = "width", nullable = false)
	private int width;

	@Column(name = "height", nullable = false)
	private int height;

	@Column(name = "is_scalable", nullable = false)
	private Boolean isScalable; // True if the icon is vector-based

	@Override
	public IconFileResponse toResponse() {
		var builder = IconFileResponse.builder()
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
		builder.width(getWidth())
			   .height(getHeight())
			   .isScalable(getIsScalable());
		return builder.build();
	}
}
