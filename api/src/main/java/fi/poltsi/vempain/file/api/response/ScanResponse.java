package fi.poltsi.vempain.file.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Response DTO for scanning a directory for new files")
public class ScanResponse {
	@Schema(description = "Indicates whether the scan was successful", example = "true")
	private boolean success;

	@Schema(description = "Error message if the scan failed", example = "Directory not found")
	private String errorMessage;

	@Schema(description = "Number of files scanned during the scan", example = "42")
	private long scannedFilesCount;

	@Schema(description = "Number of new files found during the scan", example = "5")
	private long newFilesCount;

	@Schema(description = "List of files successfully scanned", example = "/some/file.png")
	private List<String> successfulFiles;

	@Schema(description = "List of files failed to be scanned", example = "/some/other.png")
	private List<String> failedFiles;
}
