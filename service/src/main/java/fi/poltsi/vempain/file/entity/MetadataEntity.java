package fi.poltsi.vempain.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "metadata")
public class MetadataEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "file_id", nullable = false)
	private FileEntity file;

	@Column(name = "metadata_group", nullable = false, length = 128)
	private String metadataGroup;

	@Column(name = "metadata_key", nullable = false, length = 128)
	private String metadataKey;

	@Column(name = "metadata_value", nullable = false, columnDefinition = "TEXT")
	private String metadataValue;}
