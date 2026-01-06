// File: `api/src/main/java/fi/poltsi/vempain/file/api/request/ScanRequest.java`
package fi.poltsi.vempain.file.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Request DTO for scanning a directory for new files")
public class ScanRequest {

	@Nullable
	@Size.List({
			@Size(min = 2, message = "String length must be at least 2 characters"),
			@Size(max = 4096, message = "String length must be at most 4096 characters")
	})
	@Pattern(message = "Directory name must start with a slash and contain only valid characters",
			 regexp = "^/(?:[-_\\p{L}\\p{N}]+(?:/[-_\\p{L}\\p{N}]+)*/?)?$")
	@Schema(description = "Directory path, relative to the configured main directory of files, must begin with a slash-character",
			example = "/images/vacation-2025",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private String originalDirectory;

	@Nullable
	@Size.List({
			@Size(min = 2, message = "String length must be at least 2 characters"),
			@Size(max = 4096, message = "String length must be at most 4096 characters")
	})
	@Pattern(message = "Directory name must start with a slash and contain only valid characters",
			 regexp = "^/(?:[-_\\p{L}\\p{N}]+(?:/[-_\\p{L}\\p{N}]+)*/?)?$")
	@Schema(description = "Directory path, relative to the configured main directory of files, must begin with a slash-character",
			example = "/images/vacation-2025",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private String exportDirectory;

	@AssertTrue(message = "Either originalDirectory or exportedDirectory must be provided")
	public boolean isDirectoryValid() {
		return !(originalDirectory == null && exportDirectory == null);
	}
}
