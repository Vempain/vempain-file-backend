package fi.poltsi.vempain.file.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(name = "FileIngestResponse", description = "Result of file ingest operation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileIngestResponse {
	@Schema(description = "ID of the optional gallery created or added on Vempain Admin-side", example = "12345")
	private Long galleryId;

	@Schema(description = "ID of the stored site file", example = "12345")
	private Long siteFileId;

	@Schema(description = "Whether the operation updated an existing file", example = "true")
	private boolean updated;
}
