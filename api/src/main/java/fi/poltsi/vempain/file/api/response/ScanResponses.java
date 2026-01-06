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
@Schema(description = "Response DTO for directory scan results, including original and/or exported files")
public class ScanResponses {
	@Schema(description = "Result if the original files were scanned", example = "ScanResponse")
	private ScanOriginalResponse scanOriginalResponse;
	@Schema(description = "Result if the exported files were scanned", example = "ScanResponse")
	private ScanExportResponse   scanExportResponse;
}
