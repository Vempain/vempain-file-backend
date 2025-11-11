package fi.poltsi.vempain.file.api.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import fi.poltsi.vempain.file.api.GeoCoordinate;
import fi.poltsi.vempain.file.api.GuardTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Request to create or update a location guard")
public class LocationGuardRequest {

	@Schema(description = "ID of the guard, null when creating")
	private Long id;

	@Schema(description = "Guard type", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	private GuardTypeEnum guardType;

	@Schema(description = "Primary coordinate (center for circle; one corner for square)", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	@Valid
	private GeoCoordinate primaryCoordinate;

	@Schema(description = "Secondary coordinate (ignored for circle; opposite corner for square)")
	@Valid
	private GeoCoordinate secondaryCoordinate;

	@Schema(description = "Optional radius if the guard type is circle, in meters", example = "100")
	private BigDecimal radius;
}
