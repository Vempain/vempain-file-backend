package fi.poltsi.vempain.file.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
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
@Schema(description = "Request DTO for publishing one file to Vempain Admin as a site file; no gallery is created or touched")
public class PublishFileRequest {
	@Positive
	@Schema(description = "ID of the file to publish", example = "12345", requiredMode = Schema.RequiredMode.REQUIRED)
	private long fileId;

	@Nullable
	@Valid
	@Schema(description = "Additional admin users (see GET /publish/users) granted privileges on the published site file; "
						  + "the publishing service account always keeps every privilege",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private List<PublishAclRequest> acls;
}
