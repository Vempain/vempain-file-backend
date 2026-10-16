package fi.poltsi.vempain.file.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * An admin backend unit that can be granted privileges on published resources. Like the admin users, these IDs only mean something to the admin backend and are
 * never matched against this backend's own units.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Admin backend unit that can be granted privileges on published site files and galleries")
public class PublishUnitResponse {

    @Schema(description = "Admin unit ID", example = "3")
    private long id;
    @Schema(description = "Unit name", example = "Editors")
    private String name;
    @Schema(description = "Unit description", example = "Everyone who edits the site content")
    private String description;
}
