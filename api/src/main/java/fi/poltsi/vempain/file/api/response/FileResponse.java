package fi.poltsi.vempain.file.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import fi.poltsi.vempain.auth.api.response.AbstractResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Response DTO representing a file based on FileEntity")
public class FileResponse extends AbstractResponse {

	@Schema(description = "Name of the file", example = "image.png")
	private String filename;

	@Schema(description = "External file identifier", example = "ext12345")
	private String externalFileId;

	@Schema(description = "MIME type of the file", example = "image/png")
	private String mimetype;

	@Schema(description = "Size of the file in bytes", example = "102400")
	private long filesize;

	@Schema(description = "SHA-256 checksum of the file", example = "abc123def456...")
	private String sha256sum;

	@Schema(description = "Original datetime of the file", example = "2025-09-01T12:34:56Z")
	private Instant originalDatetime;

	@Schema(description = "Original second fraction", example = "123")
	private Integer originalSecondFraction;

	@Schema(description = "Original document identifier", example = "doc98765")
	private String originalDocumentId;

	@Schema(description = "Description of the file", example = "File description")
	private String description;

	@Schema(description = "File type", example = "IMAGE")
	private String fileType;

	@Schema(description = "Raw metadata as a JSON string", example = "{\"key\":\"value\"}")
	private String metadataRaw;

	@Schema(description = "List of file tags")
	private List<String> tags;
}
