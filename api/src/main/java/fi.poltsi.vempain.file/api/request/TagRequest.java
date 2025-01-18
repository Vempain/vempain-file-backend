package fi.poltsi.vempain.file.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for creating or updating a tag")
public class TagRequest {

	@NotBlank
	@Schema(description = "Tag name (default language)", example = "example-tag", requiredMode = Schema.RequiredMode.REQUIRED)
	private String tagName;

	@NotBlank
	@Schema(description = "Tag name in German", example = "beispiel-tag", requiredMode = Schema.RequiredMode.REQUIRED)
	private String tagNameDe;

	@NotBlank
	@Schema(description = "Tag name in English", example = "example-tag", requiredMode = Schema.RequiredMode.REQUIRED)
	private String tagNameEn;

	@NotBlank
	@Schema(description = "Tag name in Spanish", example = "ejemplo-etiqueta", requiredMode = Schema.RequiredMode.REQUIRED)
	private String tagNameEs;

	@NotBlank
	@Schema(description = "Tag name in Finnish", example = "esimerkki-tagi", requiredMode = Schema.RequiredMode.REQUIRED)
	private String tagNameFi;

	@NotBlank
	@Schema(description = "Tag name in Swedish", example = "exempel-tagg", requiredMode = Schema.RequiredMode.REQUIRED)
	private String tagNameSv;
}
