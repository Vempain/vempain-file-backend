package fi.poltsi.vempain.file.api.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * An additional admin-side user, picked from {@code GET /publish/users}, that is granted privileges on the site files and gallery a
 * publish creates in the admin backend. The service account this backend publishes with always keeps every privilege.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Additional admin user and the privileges it gets on the published site files and gallery")
public class PublishAclRequest {
	@Schema(description = "ID of an admin user returned by GET /publish/users", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	@Positive
	private Long userId;

	@Schema(description = "Privilege to read", example = "true")
	private boolean readPrivilege;

	@Schema(description = "Privilege to create", example = "false")
	private boolean createPrivilege;

	@Schema(description = "Privilege to modify", example = "false")
	private boolean modifyPrivilege;

	@Schema(description = "Privilege to delete", example = "false")
	private boolean deletePrivilege;

	@JsonIgnore
	@AssertTrue(message = "At least one privilege must be granted")
	public boolean isAnyPrivilege() {
		return readPrivilege || createPrivilege || modifyPrivilege || deletePrivilege;
	}
}
