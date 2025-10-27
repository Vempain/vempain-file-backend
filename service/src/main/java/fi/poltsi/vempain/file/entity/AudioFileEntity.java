package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.files.AudioFileResponse;
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
@Table(name = "audio_files")
public class AudioFileEntity extends FileEntity {

	@Column(nullable = false, precision = 5)
	private double duration;

	@Column(name = "bit_rate")
	private int bitRate;

	@Column(name = "sample_rate")
	private int sampleRate;

	@Column
	private String codec;

	@Column
	private int channels;

	@Override
	public AudioFileResponse toResponse() {
		AudioFileResponse response = new AudioFileResponse();

		// Use the parent method to populate common fields
		populateBaseResponse(response);

		// Set the specific fields for this entity type
		response.setDuration(getDuration());
		response.setBitRate(getBitRate());
		response.setSampleRate(getSampleRate());
		response.setCodec(getCodec());
		response.setChannels(getChannels());

		return response;
	}
}
