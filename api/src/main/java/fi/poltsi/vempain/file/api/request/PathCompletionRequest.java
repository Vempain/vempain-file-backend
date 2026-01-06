package fi.poltsi.vempain.file.api.request;

import fi.poltsi.vempain.file.api.PathCompletionEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Request DTO for completing a directory path")
public class PathCompletionRequest {

	@NotBlank
	@Pattern(regexp = "^/(?:[-_\\p{L}\\p{N}]+(?:/[-_\\p{L}\\p{N}]+)*/?)?$", message = "Path must start with a slash and contain valid characters")
	@Schema(description = "Path prefix to complete", example = "/three", requiredMode = Schema.RequiredMode.REQUIRED)
	private String path;

	@Schema(description = "Which type of path is sought after", example = "ORIGINAL", requiredMode = Schema.RequiredMode.REQUIRED)
	private PathCompletionEnum type;
}
