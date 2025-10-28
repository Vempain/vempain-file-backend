package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.files.VideoFileResponse;
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
@Table(name = "video_files")
public class VideoFileEntity extends FileEntity {

	@Column(name = "width", nullable = false)
	private int width;

	@Column(name = "height", nullable = false)
	private int height;

	@Column(name = "frame_rate", precision = 5)
	private double frameRate;

	@Column(nullable = false, precision = 5)
	private double duration;

	@Column
	private String codec;

	@Override
	public VideoFileResponse toResponse() {
		VideoFileResponse response = new VideoFileResponse();

		// Use the parent method to populate common fields
		populateBaseResponse(response);

		// Set the specific fields for this entity type
		response.setWidth(getWidth());
		response.setHeight(getHeight());
		response.setFrameRate(getFrameRate());
		response.setDuration(getDuration());
		response.setCodec(getCodec());

		return response;
	}
}
