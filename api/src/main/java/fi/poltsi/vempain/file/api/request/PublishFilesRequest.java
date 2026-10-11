package fi.poltsi.vempain.file.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Request for publishing selected files to the admin backend")
public class PublishFilesRequest {
	@NotEmpty
	@Schema(description = "File IDs to publish", example = "[1, 2, 3]", requiredMode = Schema.RequiredMode.REQUIRED)
	private List<@Positive Long> fileIds;
}
