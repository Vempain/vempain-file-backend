package fi.poltsi.vempain.file.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Response DTO representing an exported file")
public class ExportFileResponse {
	@Schema(description = "Export file ID", example = "1")
	private Long id;

	@Schema(description = "File ID of which this file has been exported", example = "1")
	private long file_id;

	@Schema(description = "File name of the exported file", example = "exported_image.png")
	private String filename;

	@Schema(description = "File path of the exported file", example = "/exports/2024/exported_image.png")
	private String filePath;

	@Schema(description = "MIME type of the exported file", example = "image/png")
	private String mimetype;

	@Schema(description = "File size in bytes", example = "204800")
	private long filesize;

	@Schema(description = "SHA-256 checksum of the exported file", example = "def456abc789...")
	private String sha256sum;

	@Schema(description = "Original document identifier for the exported file", example = "doc12345")
	private String originalDocumentId;

	@Schema(description = "Export date and time", example = "2025-09-01T12:34:56Z")
	private Instant created;
}
