package fi.poltsi.vempain.file.api.response.files;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO representing an archive file")
public class ArchiveFileResponse extends FileResponse {

	@Schema(description = "Compression method", example = "gzip")
	private String compressionMethod;

	@Schema(description = "Uncompressed size", example = "204800")
	private Long uncompressedSize;

	@Schema(description = "Content count", example = "5")
	private int contentCount;

	@Schema(description = "Indicates if file is encrypted", example = "true")
	private Boolean isEncrypted;
}
