package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.LocationResponse;
import fi.poltsi.vempain.file.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

	private final LocationRepository locationRepository;

	public LocationResponse getLocationById(long id) {
		var locationEntity = locationRepository.findById(id)
											   .orElse(null);

		if (locationEntity == null) {
			log.warn("Location with id {} not found", id);
			return null;
		}

		return locationEntity.toResponse();
	}
}
