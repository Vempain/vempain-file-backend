package fi.poltsi.vempain.file.api.request;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Rewrites one metadata value in every file that carries the given group, key and current value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Request to rewrite a metadata value in every file carrying the given group, key and current value")
public class MetadataValueUpdateRequest {

    @NotBlank
    @Schema(description = "ExifTool family 1 group name", example = "XMP-dc", requiredMode = Schema.RequiredMode.REQUIRED)
    private String metadataGroup;

    @NotBlank
    @Schema(description = "Metadata key inside the group", example = "Creator", requiredMode = Schema.RequiredMode.REQUIRED)
    private String metadataKey;

    @NotNull
    @Schema(description = "Current value that selects the files", example = "Jane Doe", requiredMode = Schema.RequiredMode.REQUIRED)
    private String metadataValue;

    @Nullable
    @Schema(description = "New value written into the files; null or empty removes the entry from the files", example = "Jane A. Doe",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String newValue;
}
