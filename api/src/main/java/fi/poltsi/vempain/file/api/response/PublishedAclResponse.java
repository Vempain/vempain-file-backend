package fi.poltsi.vempain.file.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * An admin-side grantee that an earlier publish put on the ACL of the site file or gallery it created: the same shape as {@code PublishAclRequest}, so that the
 * publish dialog can show the rows and send them back unchanged. The publishing service account is never listed, it always keeps every privilege.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Admin user or unit holding privileges on a previously published site file or gallery")
public class PublishedAclResponse {

    @Schema(description = "ID of the admin user (see GET /publish/users); exactly one of user_id and unit_id is set", example = "12",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long userId;

    @Schema(description = "ID of the admin unit (see GET /publish/units); exactly one of user_id and unit_id is set", example = "3",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long unitId;

    @Schema(description = "Privilege to read", example = "true")
    private boolean readPrivilege;

    @Schema(description = "Privilege to create", example = "false")
    private boolean createPrivilege;

    @Schema(description = "Privilege to modify", example = "false")
    private boolean modifyPrivilege;

    @Schema(description = "Privilege to delete", example = "false")
    private boolean deletePrivilege;
}
