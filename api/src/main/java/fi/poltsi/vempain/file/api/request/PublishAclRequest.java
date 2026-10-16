package fi.poltsi.vempain.file.api.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * An additional admin-side grantee, a user picked from {@code GET /publish/users} or a unit picked from {@code GET /publish/units}, that is granted privileges
 * on the site files and gallery a publish creates in the admin backend. The service account this backend publishes with always keeps every privilege.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Additional admin user or unit and the privileges it gets on the published site files and gallery")
public class PublishAclRequest {

    @Schema(description = "ID of an admin user returned by GET /publish/users; exactly one of user_id and unit_id is set", example = "12",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Positive
    private Long userId;

    @Schema(description = "ID of an admin unit returned by GET /publish/units; exactly one of user_id and unit_id is set", example = "3",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Positive
    private Long unitId;

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
        return readPrivilege
               || createPrivilege
               || modifyPrivilege
               || deletePrivilege;
    }

    @JsonIgnore
    @AssertTrue(message = "Exactly one of user_id and unit_id must be set")
    public boolean isUserOrUnit() {
        return (userId == null) != (unitId == null);
    }
}
