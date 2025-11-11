package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.api.request.LocationGuardRequest;
import fi.poltsi.vempain.file.api.response.LocationGuardResponse;
import fi.poltsi.vempain.file.api.response.LocationResponse;
import fi.poltsi.vempain.file.rest.LocationAPI;
import fi.poltsi.vempain.file.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LocationController implements LocationAPI {
	private final LocationService locationService;

	@Override
	public ResponseEntity<LocationResponse> getLocationById(long id) {
		var locationResponse = locationService.getLocationById(id);

		if (locationResponse == null) {
			return ResponseEntity.notFound()
								 .build();
		}

		return ResponseEntity.ok(locationResponse);
	}

	@Override
	public ResponseEntity<LocationGuardResponse> addLocationGuard(LocationGuardRequest request) {
		var resp = locationService.addLocationGuard(request);

		return ResponseEntity.ok(resp);
	}

	@Override
	public ResponseEntity<LocationGuardResponse> updateLocationGuard(LocationGuardRequest request) {
		var resp = locationService.updateLocationGuard(request);

		return ResponseEntity.ok(resp);
	}

	@Override
	public ResponseEntity<Void> deleteLocationGuard(long id) {
		locationService.deleteLocationGuard(id);

		return ResponseEntity.noContent()
							 .build();
	}

	@Override
	public ResponseEntity<List<LocationGuardResponse>> findAllLocationGuards() {
		return ResponseEntity.ok(locationService.findAll());
	}

	@Override
	public ResponseEntity<Boolean> isGuardedLocation(long gpsLocationId) {
		return ResponseEntity.ok(locationService.isGuardedLocation(gpsLocationId));
	}
}
