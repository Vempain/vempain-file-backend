package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.files.BinaryFileResponse;
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
@Table(name = "binary_files")
public class BinaryFileEntity extends FileEntity {

	@Column(name = "software_name")
	private String softwareName;

	@Column(name = "software_major_version")
	private Integer softwareMajorVersion;

	@Override
	public BinaryFileResponse toResponse() {
		BinaryFileResponse response = new BinaryFileResponse();
		populateBaseResponse(response);
		response.setSoftwareName(getSoftwareName());
		response.setSoftwareMajorVersion(getSoftwareMajorVersion());
		return response;
	}
}
