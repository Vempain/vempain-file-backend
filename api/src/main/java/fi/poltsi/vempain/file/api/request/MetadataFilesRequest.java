package fi.poltsi.vempain.file.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Paged request of the files carrying one metadata group, key and value combination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Paged request of the files that carry one metadata group, key and value")
public class MetadataFilesRequest {
	@NotBlank
	@Schema(description = "ExifTool family 1 group name", example = "XMP-dc", requiredMode = Schema.RequiredMode.REQUIRED)
	private String metadataGroup;

	@NotBlank
	@Schema(description = "Metadata key inside the group", example = "Creator", requiredMode = Schema.RequiredMode.REQUIRED)
	private String metadataKey;

	@NotNull
	@Schema(description = "Exact metadata value the files carry", example = "Jane Doe", requiredMode = Schema.RequiredMode.REQUIRED)
	private String metadataValue;

	@Schema(description = "Page number (0-based)", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
	@Min(value = 0)
	private int page;

	@Schema(description = "Size of the page", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
	@Min(value = 1)
	private int size;

	@Schema(description = "Sort column: filename, file_type or filesize (also id, file_path, mimetype, created, modified)",
			example = "filename", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	@Nullable
	private String sortBy;

	@Schema(description = "Sort direction", example = "ASC", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	@Nullable
	private Sort.Direction direction;

	@Schema(description = "Only files whose name contains this text", example = "holiday", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	@Nullable
	private String filename;

	@Schema(description = "Only files of this type", example = "IMAGE", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	@Nullable
	private FileTypeEnum fileType;
}
