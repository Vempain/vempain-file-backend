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
		IconFileResponse response = new IconFileResponse();

		// Use the parent method to populate common fields
		populateBaseResponse(response);

		// Set the specific fields for this entity type
		response.setWidth(getWidth());
		response.setHeight(getHeight());
		response.setIsScalable(getIsScalable());

		return response;
	}
}
