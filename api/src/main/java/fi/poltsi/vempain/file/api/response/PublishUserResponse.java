package fi.poltsi.vempain.file.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * An admin backend user that can be granted privileges on published resources. The admin and file user bases are separate, so these
 * IDs only mean something to the admin backend and are never matched against this backend's own accounts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Admin backend user that can be granted privileges on published site files and galleries")
public class PublishUserResponse {
	@Schema(description = "Admin user ID", example = "12")
	private long   id;
	@Schema(description = "Login name", example = "arnold")
	private String loginName;
	@Schema(description = "Full name", example = "Arnold Dunkelswetter")
	private String name;
	@Schema(description = "Nick name", example = "Ahnold")
	private String nick;
}
