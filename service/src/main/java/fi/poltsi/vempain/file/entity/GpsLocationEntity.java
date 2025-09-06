package fi.poltsi.vempain.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "gps_locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsLocationEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "latitude", nullable = false)
	private Double    latitude;
	@Column(name = "latitude_ref", nullable = false)
	private Character latitudeRef;
	@Column(name = "longitude", nullable = false)
	private Double    longitude;
	@Column(name = "longitude_ref", nullable = false)
	private Character longitudeRef;
	@Column(name = "altitude")
	private Double    altitude;
	@Column(name = "direction")
	private Double    direction;
	@Column(name = "satellite_count")
	private Integer   satelliteCount;
	@Column(name = "country")
	private String    country;
	@Column(name = "state")
	private String    state;
	@Column(name = "city")
	private String    city;
	@Column(name = "street")
	private String    street;
	@Column(name = "sub_location")
	private String    subLocation;
}
