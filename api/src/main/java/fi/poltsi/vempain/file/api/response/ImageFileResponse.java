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
@Schema(description = "Response DTO representing an image file")
public class ImageFileResponse extends FileResponse {

	@Schema(description = "Width of the image", example = "800")
	private int width;

	@Schema(description = "Height of the image", example = "600")
	private int height;

	@Schema(description = "Color depth of the image", example = "24")
	private int colorDepth;

	@Schema(description = "Dots per inch (DPI)", example = "300")
	private int dpi;

	@Schema(description = "Group label for the image, as defined in Adobe Lightroom", example = "green")
	private String groupLabel;
}
