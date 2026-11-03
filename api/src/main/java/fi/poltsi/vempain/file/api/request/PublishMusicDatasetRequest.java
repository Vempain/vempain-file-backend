package fi.poltsi.vempain.file.api.request;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Options of the music data set creation; the body is optional, without it the data set is only created in the admin backend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "PublishMusicDatasetRequest", description = "Options of the music data set creation")
public class PublishMusicDatasetRequest {

    @Schema(description = "Also publish the created data set to the website (the admin backend's own publishing task is started), default false",
            example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean publishToSite;

    @Schema(description = "Admin users and units granted privileges on the data set in the admin backend, next to the publishing service account which "
            + "always keeps every privilege (users from GET /publish/users, units from GET /publish/units). When the data set exists already the list "
            + "replaces its current grantees, an empty list removes them; null leaves them untouched.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Valid
    private List<PublishAclRequest> acls;
}
