package fi.poltsi.vempain.file.api.response.files;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO representing an audio file")
public class AudioFileResponse extends FileResponse {

	@Schema(description = "Duration of the audio", example = "3.5")
	private Duration duration;

	@Schema(description = "Bit rate of the audio", example = "320")
	private int bitRate;

	@Schema(description = "Sample rate", example = "44100")
	private int sampleRate;

	@Schema(description = "Codec used", example = "mp3")
	private String codec;

	@Schema(description = "Number of audio channels", example = "2")
	private int channels;
}
