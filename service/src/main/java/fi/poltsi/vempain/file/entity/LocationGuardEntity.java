package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.GeoCoordinate;
import fi.poltsi.vempain.file.api.GuardTypeEnum;
import fi.poltsi.vempain.file.api.response.LocationGuardResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "location_guard")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationGuardEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "guard_type", nullable = false, length = 16)
	private GuardTypeEnum guardType;

	@Column(name = "primary_longitude", nullable = false, precision = 10, scale = 5)
	private BigDecimal primaryLongitude;

	@Column(name = "primary_latitude", nullable = false, precision = 10, scale = 5)
	private BigDecimal primaryLatitude;

	@Column(name = "secondary_longitude", precision = 10, scale = 5)
	private BigDecimal secondaryLongitude;

	@Column(name = "secondary_latitude", precision = 10, scale = 5)
	private BigDecimal secondaryLatitude;

	@Column(name = "radius", precision = 10, scale = 5)
	private BigDecimal radius;

	public LocationGuardResponse toResponse() {
		var primaryCoordinate = GeoCoordinate.builder()
											 .longitude(primaryLongitude)
											 .latitude(primaryLatitude)
											 .build();

		GeoCoordinate secondaryCoordinate = null;

		if (guardType == GuardTypeEnum.SQUARE) {
			secondaryCoordinate = GeoCoordinate.builder()
											   .longitude(secondaryLongitude)
											   .latitude(secondaryLatitude)
											   .build();
		}

		var response = LocationGuardResponse.builder()
											.id(id)
											.guardType(guardType)
											.primaryCoordinate(primaryCoordinate)
											.secondaryCoordinate(secondaryCoordinate)
											.radius(radius)
											.build();
		return response;
	}
}
