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
@Schema(description = "Response DTO representing an icon file")
public class IconFileResponse extends FileResponse {

	@Schema(description = "Width of the icon", example = "64")
	private int width;

	@Schema(description = "Height of the icon", example = "64")
	private int height;

	@Schema(description = "Indicates if icon is scalable", example = "true")
	private Boolean isScalable;
}
