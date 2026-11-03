package fi.poltsi.vempain.file.api.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * A data set of the admin backend as the data set table shows it: identity, type, description, the timestamps and the admin-side audit trail. The admin
 * backend's own row ({@code DataSummaryResponse}) carries the SQL and the ACL rows too; they are not needed here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "A data set of the admin backend")
public class DataSetSummaryResponse {

    @Schema(description = "ID of the data set in the admin backend", example = "5")
    private Long id;

    @Schema(description = "ID of the ACL protecting the data set in the admin backend", example = "42")
    private long aclId;

    @Schema(description = "Unique identifier of the data set", example = "music_library")
    private String identifier;

    @Schema(description = "Type of the data set", example = "tabulated")
    private String type;

    @Schema(description = "Human-readable description", example = "Music library")
    private String description;

    @Schema(description = "When the data was generated", example = "2026-01-15T10:30:00Z")
    private Instant generated;

    @Schema(description = "Admin user that created the data set (the service account of this backend's API token)", example = "1")
    private Long creator;

    @Schema(description = "When the data set was created in the admin backend", example = "2026-01-15T10:30:00Z")
    private Instant created;

    @Schema(description = "Admin user that last changed the data set, null when never changed", example = "1")
    private Long modifier;

    @Schema(description = "When the data set was last changed in the admin backend, null when never changed", example = "2026-02-01T10:30:00Z")
    private Instant modified;

    @Schema(description = "When the data set was last released to the website, null when never", example = "2026-02-01T11:00:00Z")
    private Instant published;
}
