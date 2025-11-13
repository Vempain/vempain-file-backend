package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.files.ImageFileResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
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
		ImageFileResponse response = new ImageFileResponse();

		// Use the parent method to populate common fields
		populateBaseResponse(response);

		// Set the specific fields for this entity type
		response.setWidth(getWidth());
		response.setHeight(getHeight());
		response.setColorDepth(getColorDepth());
		response.setDpi(getDpi());
		response.setGroupLabel(getGroupLabel());

		return response;
	}
}
