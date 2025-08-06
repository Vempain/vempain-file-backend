package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.ExportFileResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "export_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportFileEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@OneToOne(optional = false)
	@JoinColumn(name = "file_id", unique = true)
	private FileEntity file;

	@Column(name = "filename", nullable = false)
	private String filename;

	@Column(name = "file_path", nullable = false)
	private String filePath;

	@Column(name = "mimetype", nullable = false)
	private String mimetype;

	@Column(name = "filesize", nullable = false)
	private long filesize;

	@Column(name = "original_document_id", nullable = true)
	private String originalDocumentId;

	@Column(name = "sha256sum", nullable = false, length = 64)
	private String sha256sum;

	@Column(name = "created", nullable = false)
	private Instant created;

	public ExportFileResponse toResponse() {
		return ExportFileResponse.builder()
								 .id(id)
								 .file_id(file.getId())
								 .filename(filename)
								 .filePath(filePath)
								 .mimetype(mimetype)
								 .filesize(filesize)
								 .sha256sum(sha256sum)
								 .originalDocumentId(originalDocumentId)
								 .created(created)
								 .build();
	}
}
