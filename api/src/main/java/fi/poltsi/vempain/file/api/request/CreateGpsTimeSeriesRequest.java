package fi.poltsi.vempain.file.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "CreateGpsTimeSeriesRequest", description = "Request to create a GPS time-series dataset from a file group")
public class CreateGpsTimeSeriesRequest {

	@Schema(description = "ID of the file group containing GPS-tagged images", example = "42", required = true)
	@NotNull(message = "File group ID must not be null")
	private Long fileGroupId;

	@Schema(description = "Name of the time-series dataset (identifier)", example = "holidays_2024", required = true)
	@NotBlank(message = "Time series name must not be blank")
	private String timeSeriesName;
}

