package fi.poltsi.vempain.file.api.request;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Sort;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Paged request of the metadata summary list. Unlike the shared {@code PagedRequest} it carries one optional contains-filter per metadata column so that the
 * list can be narrowed by key and value at the same time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Paged request of distinct metadata group/key/value rows, filtered by group, key and/or value")
public class MetadataSearchRequest {

    @Schema(description = "Page number (0-based)", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    @Min(value = 0)
    private int page;

    @Schema(description = "Size of the page", example = "25", requiredMode = Schema.RequiredMode.REQUIRED)
    @Min(value = 1)
    private int size;

    @Schema(description = "Sort column: metadata_group, metadata_key, metadata_value or file_count", example = "metadata_key",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Nullable
    private String sortBy;

    @Schema(description = "Sort direction", example = "ASC", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Nullable
    private Sort.Direction direction;

    @Schema(description = "Only rows whose group contains this text", example = "XMP", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Nullable
    private String metadataGroup;

    @Schema(description = "Only rows whose key contains this text", example = "Creator", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Nullable
    private String metadataKey;

    @Schema(description = "Only rows whose value contains this text", example = "Doe", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Nullable
    private String metadataValue;

    @Schema(description = "Whether the filters are case sensitive, false by default", example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Nullable
    private Boolean caseSensitive;
}
