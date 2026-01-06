package fi.poltsi.vempain.file.api;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "AbstractFileGroup", description = "Common file group class for both requests and responses")
@Tag(name = "AbstractFileGroup", description = "Schema for AbstractFileGroup")
public abstract class AbstractFileGroup {
	@Schema(description = "ID of the file group", example = "123")
	private Long id;

	@Schema(description = "Common path of the file group", example = "/photos/2025/holiday")
	private String path;

	@Schema(description = "Name of the file group", example = "Firenze Trip 2025")
	@NotNull
	private String groupName;

	@Schema(description = "Optional description of the group", example = "Interesting files related to our Firenze trip in 2025")
	private String description;
}

