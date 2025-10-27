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
@Schema(description = "Response DTO representing a vector file")
public class VectorFileResponse extends FileResponse {

	@Schema(description = "Width of the vector image", example = "1024")
	private int width;

	@Schema(description = "Height of the vector image", example = "768")
	private int height;

	@Schema(description = "Number of layers", example = "3")
	private int layersCount;
}
