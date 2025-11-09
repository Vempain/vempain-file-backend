package fi.poltsi.vempain.file.api.response.files;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
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
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Response DTO representing an interactive file")
public class InteractiveFileResponse extends FileResponse {

	@Schema(description = "Underlying interactive technology (e.g. FLASH, SHOCKWAVE, OTHER)", example = "FLASH")
	@Size(max = 50)
	private String technology;
}

