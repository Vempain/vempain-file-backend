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
 * Whether a data set exists in the admin backend already and, if so, who was granted privileges on it. Answered by
 * {@code GET /data-publish/{identifier}/status} before the data set dialog opens, so that the current grantees can be shown and sent back, changed or not.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Publication state of a data set in the admin backend and its ACL grantees")
public class PublishDataSetStatusResponse {

    @Schema(description = "Identifier of the data set in the admin backend", example = "music_library")
    private String identifier;

    @Schema(description = "Whether the admin backend currently holds a data set with this identifier", example = "true")
    private boolean published;

    @Schema(description = "Admin users and units holding privileges on the data set next to the publishing service account; empty when not published")
    private List<PublishedAclResponse> acls;
}
