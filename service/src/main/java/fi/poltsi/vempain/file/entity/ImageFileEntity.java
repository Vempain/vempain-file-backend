package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.ImageFileResponse;
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
@Table(name = "image_files")
public class ImageFileEntity extends FileEntity {

	@Column(name = "width", nullable = false)
	private int width;

	@Column(name = "height", nullable = false)
	private int height;

	@Column(name = "color_depth")
	private int colorDepth;

	@Column(name = "dpi")
	private int dpi;

	@Column(name = "group_label")
	private String groupLabel;

	@Override
	public ImageFileResponse toResponse() {
		// Build the response using the properties from FileEntity
		var builder = ImageFileResponse.builder()
									   .filename(getFilename())
									   .filePath(getFilePath())
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

		// Populate ImageFileEntity-specific fields
		builder.width(getWidth())
			   .height(getHeight())
			   .colorDepth(getColorDepth())
			   .dpi(getDpi())
			   .groupLabel(getGroupLabel());

		return builder.build();
	}
}
