package fi.poltsi.vempain.file.api.response.files;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Response DTO representing a thumbnail file")
public class ThumbFileResponse extends FileResponse {

	@Schema(description = "Relation type to the target file (e.g. thumbnail, screenshot)", example = "thumbnail")
	@Size(max = 50)
	private String relationType;

	@Schema(description = "ID of the file this thumbnail refers to", example = "42")
	private Long targetFileId;
}

