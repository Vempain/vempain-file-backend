package fi.poltsi.vempain.file.api.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Whether a file has been published to the admin backend before and, if so, which site file it became and who was granted privileges on it. Answered by
 * {@code GET /publish/file/{fileId}/status} before the publish dialog opens, so that the previous grantees can be shown and resent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Publication state of a file in the admin backend and the ACL grantees of its site file")
public class PublishFileStatusResponse {

    @Schema(description = "ID of the file", example = "55")
    private long fileId;

    @Schema(description = "Whether the admin backend currently holds a site file created from this file", example = "true")
    private boolean published;

    @Schema(description = "ID of the site file in the admin backend; null when not published", example = "12345",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long siteFileId;

    @Schema(description = "Admin users and units holding privileges on the site file next to the publishing service account; empty when not published")
    private List<PublishedAclResponse> acls;
}
