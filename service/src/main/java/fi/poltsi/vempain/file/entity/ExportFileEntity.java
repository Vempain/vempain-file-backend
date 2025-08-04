package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.ExportFileResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "exported_files")
@PrimaryKeyJoinColumn(name = "id")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ExportFileEntity extends FileEntity {
	@Column(name = "export_filename", nullable = false)
	private String  exportFilename;

	@Column(name = "export_file_path", nullable = false)
	private String  exportFilePath;

	@Column(name = "export_mimetype", nullable = false)
	private String exportMimetype;

	@Column(name = "export_filesize", nullable = false)
	private long exportFilesize;

	@Column(name = "export_sha256sum", nullable = false, length = 64)
	private String exportSha256sum;

	@Column(name = "original_document_id", length = 128)
	private String originalDocumentId;

	@Column(name = "export_date", nullable = false)
	private Instant exportDate;

	public ExportFileResponse toResponse() {
		return ExportFileResponse.builder()
								 // FileResponse fields
								 .id(getId())
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
								 // ExportFileResponse fields
								 .exportFilename(exportFilename)
								 .exportFilePath(exportFilePath)
								 .exportMimetype(exportMimetype)
								 .exportFilesize(exportFilesize)
								 .exportSha256sum(exportSha256sum)
								 .originalDocumentId(originalDocumentId)
								 .exportDate(exportDate)
								 .build();
	}
}
