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
@Schema(description = "Response DTO representing a font file")
public class FontFileResponse extends FileResponse {

	@Schema(description = "Font family", example = "Arial")
	private String fontFamily;

	@Schema(description = "Weight of the font", example = "Bold")
	private String weight;

	@Schema(description = "Style of the font", example = "Italic")
	private String style;
}
