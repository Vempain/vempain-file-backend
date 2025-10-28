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
@Schema(description = "Response DTO representing a document file")
public class DocumentFileResponse extends FileResponse {

	@Schema(description = "Page count", example = "10")
	private int pageCount;

	@Schema(description = "Format of the document", example = "PDF")
	private String format;
}
