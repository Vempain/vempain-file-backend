package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.AudioFileResponse;
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
		var builder = AudioFileResponse.builder()
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
		builder.duration(getDuration())
			   .bitRate(getBitRate())
			   .sampleRate(getSampleRate())
			   .codec(getCodec())
			   .channels(getChannels());
		return builder.build();
	}
}
