package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.GeoCoordinate;
import fi.poltsi.vempain.file.api.GuardTypeEnum;
import fi.poltsi.vempain.file.api.request.LocationGuardRequest;
import fi.poltsi.vempain.file.api.response.LocationGuardResponse;
import fi.poltsi.vempain.file.api.response.LocationResponse;
import fi.poltsi.vempain.file.entity.GpsLocationEntity;
import fi.poltsi.vempain.file.entity.LocationGuardEntity;
import fi.poltsi.vempain.file.repository.LocationGuardRepository;
import fi.poltsi.vempain.file.repository.LocationRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static java.math.BigDecimal.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
class LocationServiceITC {

	@Mock
	private LocationRepository      locationRepository;
	@Mock
	private LocationGuardRepository locationGuardRepository;

	@InjectMocks
	private LocationService locationService;

	private static GpsLocationEntity gps(double lat, double lon) {
		var mock   = mock(GpsLocationEntity.class);
		var absLat = Math.abs(lat);
		var absLon = Math.abs(lon);
		when(mock.getLatitude()).thenReturn(BigDecimal.valueOf(absLat));
		when(mock.getLongitude()).thenReturn(BigDecimal.valueOf(absLon));
		when(mock.getLatitudeRef()).thenReturn(lat < 0 ? 'S' : 'N');
		when(mock.getLongitudeRef()).thenReturn(lon < 0 ? 'W' : 'E');
		return mock;
	}

	@BeforeEach
	void resetMocks() {
		reset(locationRepository, locationGuardRepository);
	}

	@Nested
	@DisplayName("getLocationById")
	class GetLocationById {

		@Test
		void returnsNull_whenNotFound() {
			when(locationRepository.findById(123L)).thenReturn(Optional.empty());

			var result = locationService.getLocationById(123L);

			assertThat(result).isNull();
			verify(locationRepository).findById(123L);
		}

		@Test
		void returnsResponse_whenFound() {
			var entity   = mock(GpsLocationEntity.class);
			var response = mock(LocationResponse.class);
			when(entity.toResponse()).thenReturn(response);
			when(locationRepository.findById(5L)).thenReturn(Optional.of(entity));

			var result = locationService.getLocationById(5L);

			assertThat(result).isSameAs(response);
			verify(locationRepository).findById(5L);
			verify(entity).toResponse();
		}
	}

	@Nested
	@DisplayName("findAll guards")
	class FindAllGuards {
		@Test
		void mapsEntitiesToResponses() {
			var e1 = LocationGuardEntity.builder()
										.id(1L)
										.guardType(GuardTypeEnum.SQUARE)
										.primaryLatitude(valueOf(60.0))
										.primaryLongitude(valueOf(24.0))
										.secondaryLatitude(valueOf(60.1))
										.secondaryLongitude(valueOf(24.1))
										.build();
			var e2 = LocationGuardEntity.builder()
										.id(2L)
										.guardType(GuardTypeEnum.CIRCLE)
										.primaryLatitude(valueOf(60.2))
										.primaryLongitude(valueOf(24.2))
										// radius might be null in builder, set via setter if needed
										.build();
			e2.setRadius(valueOf(200));

			when(locationGuardRepository.findAll()).thenReturn(List.of(e1, e2));

			var results = locationService.findAll();

			assertThat(results).hasSize(2);
			assertThat(results.stream()
							  .map(LocationGuardResponse::getId)).containsExactlyInAnyOrder(1L, 2L);
			verify(locationGuardRepository).findAll();
		}
	}

	@Nested
	@DisplayName("addLocationGuard")
	class AddLocationGuard {

		@Test
		void createsCircleGuard_andPersists() {
			var primaryCoordinate = GeoCoordinate.builder()
												 .longitude(valueOf(24.93545))
												 .latitude(valueOf(60.16952))
												 .build();
			var locationGuardRequest = LocationGuardRequest.builder()
														   .guardType(GuardTypeEnum.CIRCLE)
														   .primaryCoordinate(primaryCoordinate)
														   .radius(BigDecimal.valueOf(100.5))
														   .build();
			// Note: radius optional in request; service copies if present. We focus on lat/lon mapping.
			var saved = LocationGuardEntity.builder()
										   .id(10L)
										   .guardType(GuardTypeEnum.CIRCLE)
										   .primaryLongitude(valueOf(24.93545).setScale(5))
										   .primaryLatitude(valueOf(60.16952).setScale(5))
										   .build();

			when(locationGuardRepository.save(any(LocationGuardEntity.class))).thenReturn(saved);

			var resp = locationService.addLocationGuard(locationGuardRequest);

			assertThat(resp.getId()).isEqualTo(10L);
			assertThat(resp.getGuardType()).isEqualTo(GuardTypeEnum.CIRCLE);

			var captor = ArgumentCaptor.forClass(LocationGuardEntity.class);
			verify(locationGuardRepository).save(captor.capture());
			var toSave = captor.getValue();
			assertThat(toSave.getGuardType()).isEqualTo(GuardTypeEnum.CIRCLE);
			assertThat(toSave.getPrimaryLatitude()).isEqualTo(valueOf(60.16952).setScale(5, BigDecimal.ROUND_HALF_UP));
			assertThat(toSave.getPrimaryLongitude()).isEqualTo(valueOf(24.93545).setScale(5, BigDecimal.ROUND_HALF_UP));
		}

		@Test
		void createsSquareGuard_andPersists() {
			var primaryCoordinate = GeoCoordinate.builder()
												 .longitude(valueOf(24.90000))
												 .latitude(valueOf(60.10000))
												 .build();
			var secondaryCoordinate = GeoCoordinate.builder()
												   .longitude(valueOf(24.95000))
												   .latitude(valueOf(60.15000))
												   .build();

			var locationGuardRequest = LocationGuardRequest.builder()
														   .guardType(GuardTypeEnum.SQUARE)
														   .primaryCoordinate(primaryCoordinate)
														   .secondaryCoordinate(secondaryCoordinate)
														   .build();
			var saved = LocationGuardEntity.builder()
										   .id(11L)
										   .guardType(GuardTypeEnum.SQUARE)
										   .primaryLongitude(valueOf(24.90000).setScale(5))
										   .primaryLatitude(valueOf(60.10000).setScale(5))
										   .secondaryLongitude(valueOf(24.95000).setScale(5))
										   .secondaryLatitude(valueOf(60.15000).setScale(5))
										   .build();

			when(locationGuardRepository.save(any(LocationGuardEntity.class))).thenReturn(saved);

			var resp = locationService.addLocationGuard(locationGuardRequest);

			assertThat(resp.getId()).isEqualTo(11L);
			assertThat(resp.getGuardType()).isEqualTo(GuardTypeEnum.SQUARE);

			verify(locationGuardRepository).save(any(LocationGuardEntity.class));
		}
	}

	@Nested
	@DisplayName("updateLocationGuard")
	class UpdateLocationGuard {

		@Test
		void throws_whenIdMissing() {
			var primaryCoordinate = GeoCoordinate.builder()
												 .longitude(valueOf(24.9))
												 .latitude(valueOf(60.1))
												 .build();
			var secondaryCoordinate = GeoCoordinate.builder()
												   .longitude(valueOf(24.95))
												   .latitude(valueOf(60.15))
												   .build();
			var locationGuardRequest = LocationGuardRequest.builder()
														   .guardType(GuardTypeEnum.SQUARE)
														   .primaryCoordinate(primaryCoordinate)
														   .secondaryCoordinate(secondaryCoordinate)
														   .build();

			assertThrows(IllegalArgumentException.class, () -> locationService.updateLocationGuard(locationGuardRequest));
			verifyNoInteractions(locationGuardRepository);
		}

		@Test
		void updatesExisting_toCircle() {
			var existing = LocationGuardEntity.builder()
											  .id(100L)
											  .guardType(GuardTypeEnum.SQUARE)
											  .primaryLongitude(valueOf(24.9))
											  .primaryLatitude(valueOf(60.1))
											  .secondaryLongitude(valueOf(24.95))
											  .secondaryLatitude(valueOf(60.15))
											  .build();

			when(locationGuardRepository.findById(100L)).thenReturn(Optional.of(existing));
			when(locationGuardRepository.save(any(LocationGuardEntity.class))).thenAnswer(inv -> inv.getArgument(0, LocationGuardEntity.class));

			var primaryCoordinate = GeoCoordinate.builder()
												 .longitude(valueOf(24.93500))
												 .latitude(valueOf(60.17000))
												 .build();
			var locationGuardRequest = LocationGuardRequest.builder()
														   .id(100L)
														   .guardType(GuardTypeEnum.CIRCLE)
														   .primaryCoordinate(primaryCoordinate)
														   .build();

			var resp = locationService.updateLocationGuard(locationGuardRequest);

			assertThat(resp.getId()).isEqualTo(100L);
			assertThat(resp.getGuardType()).isEqualTo(GuardTypeEnum.CIRCLE);

			var captor = ArgumentCaptor.forClass(LocationGuardEntity.class);
			verify(locationGuardRepository).save(captor.capture());
			var saved = captor.getValue();
			assertThat(saved.getGuardType()).isEqualTo(GuardTypeEnum.CIRCLE);
			assertThat(saved.getSecondaryLatitude()).isNull();
			assertThat(saved.getSecondaryLongitude()).isNull();
		}

		@Test
		void updatesExisting_toSquare() {
			var existing = LocationGuardEntity.builder()
											  .id(101L)
											  .guardType(GuardTypeEnum.CIRCLE)
											  .primaryLongitude(valueOf(24.9))
											  .primaryLatitude(valueOf(60.1))
											  .build();
			existing.setRadius(valueOf(300));

			when(locationGuardRepository.findById(101L)).thenReturn(Optional.of(existing));
			when(locationGuardRepository.save(any(LocationGuardEntity.class))).thenAnswer(inv -> inv.getArgument(0, LocationGuardEntity.class));

			var primaryCoordinate = GeoCoordinate.builder()
												 .longitude(valueOf(24.90))
												 .latitude(valueOf(60.10))
												 .build();
			var secondaryCoordinate = GeoCoordinate.builder()
												   .longitude(valueOf(24.95))
												   .latitude(valueOf(60.15))
												   .build();
			var locationGuardRequest = LocationGuardRequest.builder()
														   .id(101L)
														   .guardType(GuardTypeEnum.SQUARE)
														   .primaryCoordinate(primaryCoordinate)
														   .secondaryCoordinate(secondaryCoordinate)
														   .build();

			var resp = locationService.updateLocationGuard(locationGuardRequest);

			assertThat(resp.getId()).isEqualTo(101L);
			assertThat(resp.getGuardType()).isEqualTo(GuardTypeEnum.SQUARE);

			var captor = ArgumentCaptor.forClass(LocationGuardEntity.class);
			verify(locationGuardRepository).save(captor.capture());
			var saved = captor.getValue();
			assertThat(saved.getGuardType()).isEqualTo(GuardTypeEnum.SQUARE);
			assertThat(saved.getRadius()).isNull();
			assertThat(saved.getSecondaryLongitude()).isNotNull();
			assertThat(saved.getSecondaryLatitude()).isNotNull();
		}
	}

	@Nested
	@DisplayName("deleteLocationGuard")
	class DeleteLocationGuard {

		@Test
		void noDelete_whenNotExists() {
			when(locationGuardRepository.existsById(555L)).thenReturn(false);

			locationService.deleteLocationGuard(555L);

			verify(locationGuardRepository, never()).deleteById(anyLong());
		}

		@Test
		void deletes_whenExists() {
			when(locationGuardRepository.existsById(777L)).thenReturn(true);

			locationService.deleteLocationGuard(777L);

			verify(locationGuardRepository).deleteById(777L);
		}
	}

	@Nested
	@DisplayName("isGuardedLocation (SQUARE)")
	class IsGuardedSquare {

		@ParameterizedTest(name = "lat={0}, lon={1} -> inside={2}")
		@CsvSource({
				"60.1200, 24.9200, true",   // inside box
				"60.1000, 24.9000, true",   // on lower-left edge
				"60.1500, 24.9500, true",   // on upper-right edge
				"60.0950, 24.9200, false",  // below
				"60.1600, 24.9200, false",  // above
				"60.1200, 24.8950, false",  // left
				"60.1200, 24.9600, false"   // right
		})
		void evaluatesInsideForSquare(double lat, double lon, boolean expectedInside) {
			var square = LocationGuardEntity.builder()
											.guardType(GuardTypeEnum.SQUARE)
											.primaryLatitude(valueOf(60.10000))
											.primaryLongitude(valueOf(24.90000))
											.secondaryLatitude(valueOf(60.15000))
											.secondaryLongitude(valueOf(24.95000))
											.build();

			when(locationGuardRepository.findAll()).thenReturn(List.of(square));

			var gps = gps(lat, lon);

			assertThat(locationService.isGuardedLocation(gps)).isEqualTo(expectedInside);
		}
	}

	@Nested
	@DisplayName("isGuardedLocation (CIRCLE)")
	class IsGuardedCircle {

		@ParameterizedTest(name = "latOffsetDeg={0}, lonOffsetDeg={1} -> inside={2}")
		@CsvSource({
				"0.0005, 0.0, true",   // ~55m north
				"0.0000, 0.0010, true",// ~55-70m east (depends on latitude)
				"0.0015, 0.0000, true",// ~167m north
				"0.0030, 0.0000, false",// ~333m north (outside)
				"0.0000, 0.0040, false" // far east (outside)
		})
		void evaluatesInsideForCircle(double latOffsetDeg, double lonOffsetDeg, boolean expectedInside) {
			// center = (60.1700, 24.9350), radius = 200m
			double centerLat = 60.1700;
			double centerLon = 24.9350;
			var locationGuardEntity = LocationGuardEntity.builder()
														 .guardType(GuardTypeEnum.CIRCLE)
														 .primaryLatitude(valueOf(centerLat))
														 .primaryLongitude(valueOf(centerLon))
														 .radius(valueOf(200))
														 .build();

			when(locationGuardRepository.findAll()).thenReturn(List.of(locationGuardEntity));

			var offsetLatitude    = centerLat + latOffsetDeg;
			var offsetLongitude   = centerLon + lonOffsetDeg;
			var gpsLocationEntity = gps(offsetLatitude, offsetLongitude);
			log.info("Check distance between center ({}, {}) and test point ({}, {})", centerLat, centerLon, offsetLatitude, offsetLongitude);

			assertEquals(expectedInside, locationService.isGuardedLocation(gpsLocationEntity));
		}
	}

	@Nested
	@DisplayName("isGuardedLocation by id")
	class IsGuardedById {

		@Test
		void returnsFalse_whenLocationMissing() {
			when(locationRepository.findById(999L)).thenReturn(Optional.empty());

			assertThat(locationService.isGuardedLocation(999L)).isFalse();
		}

		@Test
		void delegatesToGuardCheck_whenLocationExists() {
			// Include a square guard (60.10..60.15 , 24.90..24.95)
			var square = LocationGuardEntity.builder()
											.guardType(GuardTypeEnum.SQUARE)
											.primaryLatitude(valueOf(60.10000))
											.primaryLongitude(valueOf(24.90000))
											.secondaryLatitude(valueOf(60.15000))
											.secondaryLongitude(valueOf(24.95000))
											.build();
			when(locationGuardRepository.findAll()).thenReturn(List.of(square));

			var gps = gps(60.12, 24.92);
			when(locationRepository.findById(1L)).thenReturn(Optional.of(gps));

			assertThat(locationService.isGuardedLocation(1L)).isTrue();
			verify(locationRepository).findById(1L);
		}
	}
}

