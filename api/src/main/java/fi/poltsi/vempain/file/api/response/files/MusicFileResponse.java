package fi.poltsi.vempain.file.api.response.files;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO representing a music file with artist/album metadata")
public class MusicFileResponse extends AudioFileResponse {

	@Schema(description = "Recording artist or band", example = "The Beatles")
	private String artist;

	@Schema(description = "Album name", example = "Abbey Road")
	private String album;

	@Schema(description = "Track title", example = "Come Together")
	private String trackName;

	@Schema(description = "Track number within the album", example = "1")
	private Integer trackNumber;

	@Schema(description = "Music genre", example = "Rock")
	private String genre;
}
