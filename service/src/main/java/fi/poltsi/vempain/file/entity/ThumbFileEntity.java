package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.files.ThumbFileResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "thumb_files")
public class ThumbFileEntity extends FileEntity {

	// The file this thumbnail refers to (if known)
	@ManyToOne
	@JoinColumn(name = "target_file_id")
	private FileEntity targetFile;

	// Relationship type, e.g. "thumbnail", "screenshot"
	@Column(name = "relation_type")
	private String relationType;

	@Override
	public ThumbFileResponse toResponse() {
		ThumbFileResponse response = new ThumbFileResponse();
		populateBaseResponse(response);
		response.setRelationType(getRelationType());
		response.setTargetFileId(getTargetFile() == null ? null : getTargetFile().getId());
		return response;
	}
}
