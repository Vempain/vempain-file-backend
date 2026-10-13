package fi.poltsi.vempain.file.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "One distinct metadata group, key and value combination together with the number of files carrying it")
public class MetadataSummaryResponse {

	@Schema(description = "ExifTool family 1 group name of the metadata entry", example = "XMP-dc")
	private String metadataGroup;

	@Schema(description = "Metadata key (ExifTool tag name) inside the group", example = "Creator")
	private String metadataKey;

	@Schema(description = "Metadata value as stored by the scanner", example = "Jane Doe")
	private String metadataValue;

	@Schema(description = "Number of files carrying exactly this group, key and value", example = "42")
	private long fileCount;

	@Schema(description = "Whether the value can be rewritten into the files; informational groups such as File, System, Composite "
						  + "and ExifTool are read-only", example = "true")
	private boolean writable;
}
