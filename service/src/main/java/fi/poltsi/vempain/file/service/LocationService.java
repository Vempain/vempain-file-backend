package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.GuardTypeEnum;
import fi.poltsi.vempain.file.api.request.LocationGuardRequest;
import fi.poltsi.vempain.file.api.response.LocationGuardResponse;
import fi.poltsi.vempain.file.api.response.LocationResponse;
import fi.poltsi.vempain.file.entity.GpsLocationEntity;
import fi.poltsi.vempain.file.entity.LocationGuardEntity;
import fi.poltsi.vempain.file.repository.LocationGuardRepository;
import fi.poltsi.vempain.file.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

	private final LocationRepository locationRepository;
	private final LocationGuardRepository locationGuardRepository;

	public LocationResponse getLocationById(long id) {
		var locationEntity = locationRepository.findById(id)
											   .orElse(null);

		if (locationEntity == null) {
			log.warn("Location with id {} not found", id);
			return null;
		}

		return locationEntity.toResponse();
	}

	private static BigDecimal correctCoordinateScale(BigDecimal v) {
		return v == null ? null : v.setScale(5, RoundingMode.HALF_UP);
	}

	private static double toSignedLat(GpsLocationEntity gps) {
		var v = gps.getLatitude()
				   .abs()
				   .doubleValue();
		var ref = gps.getLatitudeRef();
		if (ref != null && (ref == 'S' || ref == 's')) {
			v = -v;
		}
		return v;
	}

	private static double toSignedLon(GpsLocationEntity gps) {
		var v = gps.getLongitude()
				   .abs()
				   .doubleValue();
		var ref = gps.getLongitudeRef();
		if (ref != null && (ref == 'W' || ref == 'w')) {
			v = -v;
		}
		return v;
	}

	private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
		var R    = 6371000.0; // meters
		var dLat = Math.toRadians(lat2 - lat1);
		var dLon = Math.toRadians(lon2 - lon1);
		var a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				+ 0.0 * Math.sin(0) // placeholder to keep format consistent
				+ Math.sin(dLon / 2) * Math.sin(dLon / 2);
		// Fix formula (remove placeholder influence)
		a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
			+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
			  * Math.sin(dLon / 2) * Math.sin(dLon / 2);
		var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return R * c;
	}

	public List<LocationGuardResponse> findAll() {
		return locationGuardRepository.findAll()
									  .stream()
									  .map(LocationGuardEntity::toResponse)
									  .toList();
	}

	public LocationGuardResponse addLocationGuard(LocationGuardRequest locationGuardRequest) {
		var locationGuardEntity = LocationGuardEntity.builder()
													 .guardType(locationGuardRequest.getGuardType())
													 .primaryLongitude(correctCoordinateScale(locationGuardRequest.getPrimaryCoordinate()
																												  .getLongitude()))
													 .primaryLatitude(correctCoordinateScale(locationGuardRequest.getPrimaryCoordinate()
																												 .getLatitude()))
													 .build();

		if (locationGuardEntity.getGuardType()
							   .equals(GuardTypeEnum.CIRCLE)) {
			locationGuardEntity.setRadius(locationGuardRequest.getRadius());
		} else {
			locationGuardEntity.setSecondaryLongitude(correctCoordinateScale(locationGuardRequest.getSecondaryCoordinate()
																								 .getLongitude()));
			locationGuardEntity.setSecondaryLatitude(correctCoordinateScale(locationGuardRequest.getSecondaryCoordinate()
																								.getLatitude()));
		}

		var newEntity = locationGuardRepository.save(locationGuardEntity);
		return newEntity.toResponse();
	}

	public LocationGuardResponse updateLocationGuard(LocationGuardRequest locationGuardRequest) {
		if (locationGuardRequest.getId() == null) {
			throw new IllegalArgumentException("LocationGuardRequest.id must not be null for update");
		}

		var entity = locationGuardRepository.findById(locationGuardRequest.getId())
											.orElseThrow(() -> new IllegalArgumentException("LocationGuard not found: " + locationGuardRequest.getId()));
		// overwrite fields from request
		entity.setGuardType(locationGuardRequest.getGuardType());
		entity.setPrimaryLongitude(correctCoordinateScale(locationGuardRequest.getPrimaryCoordinate()
																			  .getLongitude()));
		entity.setPrimaryLatitude(correctCoordinateScale(locationGuardRequest.getPrimaryCoordinate()
																			 .getLatitude()));

		if (locationGuardRequest.getGuardType()
								.equals(GuardTypeEnum.CIRCLE)) {
			entity.setRadius(locationGuardRequest.getRadius());
			entity.setSecondaryLongitude(null);
			entity.setSecondaryLatitude(null);
		} else {
			entity.setSecondaryLongitude(correctCoordinateScale(locationGuardRequest.getSecondaryCoordinate()
																					.getLongitude()));
			entity.setSecondaryLatitude(correctCoordinateScale(locationGuardRequest.getSecondaryCoordinate()
																				   .getLatitude()));
			entity.setRadius(null);
		}

		var updatedEntity = locationGuardRepository.save(entity);
		return updatedEntity.toResponse();
	}

	public void deleteLocationGuard(long id) {
		if (!locationGuardRepository.existsById(id)) {
			log.warn("LocationGuard with id {} not found, nothing to delete", id);
			return;
		}

		locationGuardRepository.deleteById(id);
	}

	// New method: checks whether given GPS location is inside any guard
	public boolean isGuardedLocation(GpsLocationEntity gps) {
		if (gps == null || gps.getLatitude() == null || gps.getLongitude() == null) {
			return false;
		}

		final var lat = toSignedLat(gps);
		final var lon = toSignedLon(gps);

		for (var guard : locationGuardRepository.findAll()) {
			if (guard.getGuardType() == GuardTypeEnum.SQUARE) {
				// Require both corners
				if (guard.getPrimaryLatitude() == null || guard.getPrimaryLongitude() == null
					|| guard.getSecondaryLatitude() == null || guard.getSecondaryLongitude() == null) {
					continue;
				}
				var gLat1 = guard.getPrimaryLatitude()
								 .doubleValue();
				var gLon1 = guard.getPrimaryLongitude()
								 .doubleValue();
				var gLat2 = guard.getSecondaryLatitude()
								 .doubleValue();
				var gLon2 = guard.getSecondaryLongitude()
								 .doubleValue();

				var minLat = Math.min(gLat1, gLat2);
				var maxLat = Math.max(gLat1, gLat2);
				var minLon = Math.min(gLon1, gLon2);
				var maxLon = Math.max(gLon1, gLon2);

				if (lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon) {
					return true;
				}
			} else if (guard.getGuardType() == GuardTypeEnum.CIRCLE) {
				// Center is in primary coord; radius in meters
				if (guard.getPrimaryLatitude() == null || guard.getPrimaryLongitude() == null || guard.getRadius() == null) {
					continue;
				}
				var centerLat = guard.getPrimaryLatitude()
									 .doubleValue();
				var centerLon = guard.getPrimaryLongitude()
									 .doubleValue();
				var radiusMeters = guard.getRadius()
										.doubleValue();
				if (radiusMeters <= 0) {
					continue;
				}
				var dist = haversineMeters(lat, lon, centerLat, centerLon);
				if (dist <= radiusMeters) {
					return true;
				}
			}
		}
		return false;
	}

	// Overload used by REST API: resolve entity by ID then delegate
	public boolean isGuardedLocation(long gpsLocationId) {
		var gps = locationRepository.findById(gpsLocationId)
									.orElse(null);
		if (gps == null) {
			return false;
		}
		return isGuardedLocation(gps);
	}
}
