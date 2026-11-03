package fi.poltsi.vempain.file.api.request;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Paged request of the data set table ({@code POST /data-publish/paged}), forwarded to the admin backend's paged data set listing; the admin backend filters,
 * sorts and pages.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Paged data set request")
public class DataSetPagedRequest extends PagedRequest {

    @Schema(description = "Only data sets of this type (exact, case insensitive), for example time_series or tabulated", example = "time_series",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String type;

    @Schema(description = "Data set field the search is restricted to: identifier, type or description; without it the search covers all three",
            example = "identifier", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String filterColumn;

    @Schema(description = "When true, only data sets modified after their last release to the website are listed", example = "false",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean modifiedAfterPublish;
}
