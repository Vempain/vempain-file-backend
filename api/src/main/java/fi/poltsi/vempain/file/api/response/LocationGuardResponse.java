package fi.poltsi.vempain.file.api.response;

import fi.poltsi.vempain.file.api.GeoCoordinate;
import fi.poltsi.vempain.file.api.GuardTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Response DTO for a location guard")
public class LocationGuardResponse {

	@Schema(description = "ID of the guard", example = "42")
	@NotNull
	private Long id;

	@Schema(description = "Guard type", example = "SQUARE")
	@NotNull
	private GuardTypeEnum guardType;

	@Schema(description = "Primary coordinate")
	@NotNull
	@Valid
	private GeoCoordinate primaryCoordinate;

	@Schema(description = "Secondary coordinate (may be null for circle)")
	@Valid
	private GeoCoordinate secondaryCoordinate;

	@Schema(description = "Optional radius if the guard type is circle, in meters", example = "100")
	private BigDecimal radius;
}

