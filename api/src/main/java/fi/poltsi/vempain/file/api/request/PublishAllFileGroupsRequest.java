package fi.poltsi.vempain.file.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
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
@Schema(description = "Request DTO for publishing every file group the caller may modify; the ACL entries apply to every published group")
public class PublishAllFileGroupsRequest {
	@Nullable
	@Valid
	@Schema(description = "Additional admin users granted privileges on every published site file and gallery", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private List<PublishAclRequest> acls;
}
