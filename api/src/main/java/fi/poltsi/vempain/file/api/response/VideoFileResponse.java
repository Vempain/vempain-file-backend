package fi.poltsi.vempain.file.api.response;

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
@Schema(description = "Response DTO representing a video file")
public class VideoFileResponse extends FileResponse {

	@Schema(description = "Width of the video", example = "1920")
	private int width;

	@Schema(description = "Height of the video", example = "1080")
	private int height;

	@Schema(description = "Frame rate of the video", example = "24.0")
	private double frameRate;

	@Schema(description = "Duration of the video", example = "120.0")
	private double duration;

	@Schema(description = "Codec used for the video", example = "H.264")
	private String codec;
}
