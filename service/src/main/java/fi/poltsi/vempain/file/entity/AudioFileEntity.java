package fi.poltsi.vempain.file.entity;

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
}
