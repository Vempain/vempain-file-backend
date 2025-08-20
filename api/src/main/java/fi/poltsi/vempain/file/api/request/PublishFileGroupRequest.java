package fi.poltsi.vempain.file.api.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Request DTO for publishing a file group in Vempain Admin, possibly also create a new gallery")
public class PublishFileGroupRequest {
	@Schema(description = "ID of the file group to publish", example = "12345", requiredMode = Schema.RequiredMode.REQUIRED)
	private long fileGroupId;

	@Nullable
	@Schema(description = "Optional gallery name", example = "Some gallery", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private String galleryName;

	@Nullable
	@Schema(description = "Optional gallery description", example = "This is a description of the gallery", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private String galleryDescription;
}
