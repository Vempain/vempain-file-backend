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
 * Whether a file group has been published to the admin backend before and, if so, which gallery it became, which site files the gallery holds and who was
 * granted privileges on it. Answered by {@code GET /publish/file-group/{fileGroupId}/status} before the publish dialog opens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Publication state of a file group in the admin backend and the ACL grantees of its gallery")
public class PublishFileGroupStatusResponse {

    @Schema(description = "ID of the file group", example = "11")
    private long fileGroupId;

    @Schema(description = "Whether the admin backend currently holds the gallery this group was published as", example = "true")
    private boolean published;

    @Schema(description = "ID of the gallery in the admin backend; null when not published", example = "1001", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long galleryId;

    @Schema(description = "Short name of the gallery in the admin backend; null when not published", example = "Summer 2025",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String galleryName;

    @Schema(description = "IDs of the site files linked to the gallery, in gallery order; empty when not published", example = "[12345, 12346]")
    private List<Long> siteFileIds;

    @Schema(description = "Admin users and units holding privileges on the gallery next to the publishing service account; empty when not published")
    private List<PublishedAclResponse> acls;
}
