package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.VectorFileResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.stream.Collectors;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vector_files")
public class VectorFileEntity extends FileEntity {

	@Column(name = "width", nullable = false)
	private int width;

	@Column(name = "height", nullable = false)
	private int height;

	@Column(name = "layers_count")
	private int layersCount; // Optional: Number of layers

	@Override
	public VectorFileResponse toResponse() {
		var builder = VectorFileResponse.builder()
										.filename(getFilename())
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
										.tags(getTags().stream()
													   .map(TagEntity::getTagName)
													   .collect(Collectors.toList()));
		builder.width(getWidth())
			   .height(getHeight())
			   .layersCount(getLayersCount());
		return builder.build();
	}
}
