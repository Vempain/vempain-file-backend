package fi.poltsi.vempain.file.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Response DTO for tag details")
public class TagResponse {

	@Schema(description = "Unique identifier of the tag", example = "1")
	private Long id;

	@Schema(description = "Tag name (default language)", example = "example-tag")
	private String tagName;

	@Schema(description = "Tag name in German", example = "beispiel-tag")
	private String tagNameDe;

	@Schema(description = "Tag name in English", example = "example-tag")
	private String tagNameEn;

	@Schema(description = "Tag name in Spanish", example = "ejemplo-etiqueta")
	private String tagNameEs;

	@Schema(description = "Tag name in Finnish", example = "esimerkki-tagi")
	private String tagNameFi;

	@Schema(description = "Tag name in Swedish", example = "exempel-tagg")
	private String tagNameSv;
}
