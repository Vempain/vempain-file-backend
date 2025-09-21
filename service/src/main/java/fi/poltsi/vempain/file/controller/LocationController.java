package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.api.response.LocationResponse;
import fi.poltsi.vempain.file.rest.LocationAPI;
import fi.poltsi.vempain.file.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

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
}
