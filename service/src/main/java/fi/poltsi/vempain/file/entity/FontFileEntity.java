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
	private String fontFamily; // Font family name

	@Column(nullable = false)
	private String weight; // Font weight (e.g., "regular", "bold")

	@Column(nullable = false)
	private String style; // Font style (e.g., "normal", "italic")

	@Override
	public FontFileResponse toResponse() {
		FontFileResponse response = new FontFileResponse();

		// Use the parent method to populate common fields
		populateBaseResponse(response);

		// Set the specific fields for this entity type
		response.setFontFamily(getFontFamily());
		response.setWeight(getWeight());
		response.setStyle(getStyle());

		return response;
	}
}
