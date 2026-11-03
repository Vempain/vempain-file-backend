package fi.poltsi.vempain.file.api.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "CreateGpsTimeSeriesRequest", description = "Request to create a GPS time-series dataset from a file group")
public class CreateGpsTimeSeriesRequest {

    @Schema(description = "ID of the file group containing GPS-tagged images", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "File group ID must not be null")
    private Long fileGroupId;

    @Schema(description = "Name of the time-series dataset (identifier)", example = "holidays_2024", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Time series name must not be blank")
    private String timeSeriesName;

    @Schema(description = "Also publish the created data set to the website (the admin backend's own publishing task is started), default false",
            example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean publishToSite;

    @Schema(description = "Admin users and units granted privileges on the data set in the admin backend, next to the publishing service account which "
            + "always keeps every privilege (users from GET /publish/users, units from GET /publish/units). When the data set exists already the list "
            + "replaces its current grantees, an empty list removes them; null leaves them untouched.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Valid
    private List<PublishAclRequest> acls;
}
