package fi.poltsi.vempain.file.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
@Schema(description = "Geographic coordinate pair (WGS84)")
public class GeoCoordinate {

	@Schema(description = "Longitude in decimal degrees", example = "24.93545")
	@NotNull
	@DecimalMin(value = "-180.0")
	@DecimalMax(value = "180.0")
	@Digits(integer = 5, fraction = 5)
	private BigDecimal longitude;

	@Schema(description = "Latitude in decimal degrees", example = "60.16952")
	@NotNull
	@DecimalMin(value = "-90.0")
	@DecimalMax(value = "90.0")
	@Digits(integer = 5, fraction = 5)
	private BigDecimal latitude;
}

