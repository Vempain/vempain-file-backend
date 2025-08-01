package fi.poltsi.vempain.file.api.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Request DTO for scanning a directory for new files")
public class ScanRequest {

	@NotNull
	@NotBlank
	@Size.List({
			@Size(min = 2, message = "String length must be at least 2 characters"),
			@Size(max = 4096, message = "String length must be at most 4096 characters")
	})
	@Pattern(message = "Directory name must start with a slash and contain only valid characters",
			 regexp = "^/(?:[-_\\p{L}\\p{N}]+(?:/[-_\\p{L}\\p{N}]+)*/?)?$")
	@Schema(description = "Directory path, relative to the configured main directory of files, must begin with a slash-character",
			example = "/images/vacation-2025",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String directoryName;
}
