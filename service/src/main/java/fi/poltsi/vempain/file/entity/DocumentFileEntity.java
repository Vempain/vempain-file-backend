package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.DocumentFileResponse;
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
@Table(name = "document_files")
public class DocumentFileEntity extends FileEntity {

	@Column(name = "page_count")
	private int pageCount; // Number of pages in the document

	@Column
	private String format; // Document format (e.g., PDF, DOCX)

	@Override
	public DocumentFileResponse toResponse() {
		DocumentFileResponse response = new DocumentFileResponse();

		// Use the parent method to populate common fields
		populateBaseResponse(response);

		// Set the specific fields for this entity type
		response.setPageCount(getPageCount());
		response.setFormat(getFormat());

		return response;
	}
}
