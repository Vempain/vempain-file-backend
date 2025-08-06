package fi.poltsi.vempain.file.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
