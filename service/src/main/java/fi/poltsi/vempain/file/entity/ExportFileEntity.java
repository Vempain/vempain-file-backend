package fi.poltsi.vempain.file.entity;

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
	@Column(name = "export_date", nullable = false)
	private Instant exportDate;
}
