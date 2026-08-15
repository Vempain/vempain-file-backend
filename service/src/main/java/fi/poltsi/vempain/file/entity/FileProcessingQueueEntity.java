package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.FileTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "file_processing_queue")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileProcessingQueueEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "file_id", nullable = false, unique = true)
	private FileEntity file;

	@Enumerated(EnumType.STRING)
	@Column(name = "file_type", nullable = false, length = 32)
	private FileTypeEnum fileType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private FileProcessingStatus status;

	@Column(name = "attempts", nullable = false)
	private int attempts;

	@Column(name = "created", nullable = false)
	private Instant created;

	@Column(name = "updated", nullable = false)
	private Instant updated;

	@Column(name = "started")
	private Instant started;

	@Column(name = "completed")
	private Instant completed;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;
}
