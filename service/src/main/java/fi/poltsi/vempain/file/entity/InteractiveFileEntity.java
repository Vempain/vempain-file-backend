package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.files.InteractiveFileResponse;
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
@Table(name = "interactive_files")
public class InteractiveFileEntity extends FileEntity {

	// e.g. FLASH, SHOCKWAVE, OTHER
	@Column(name = "technology")
	private String technology;

	@Override
	public InteractiveFileResponse toResponse() {
		InteractiveFileResponse response = new InteractiveFileResponse();
		populateBaseResponse(response);
		response.setTechnology(getTechnology());
		return response;
	}
}
