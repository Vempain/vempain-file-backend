package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.files.DataFileResponse;
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
@Table(name = "data_files")
public class DataFileEntity extends FileEntity {

	// e.g. JSON, XML, CSV, NDJSON, YAML, BINARY, OTHER
	@Column(name = "data_structure")
	private String dataStructure;

	@Override
	public DataFileResponse toResponse() {
		DataFileResponse response = new DataFileResponse();
		populateBaseResponse(response);
		response.setDataStructure(getDataStructure());
		return response;
	}
}
