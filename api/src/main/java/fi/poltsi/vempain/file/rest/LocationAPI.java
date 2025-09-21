package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.LocationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Location API", description = "API for accessing and managing location data")
public interface LocationAPI {
	String BASE_PATH = "/location";

	@Operation(summary = "Get location data by ID", description = "Retrieve specific location data by its unique identifier")
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<LocationResponse> getLocationById(@PathVariable long id);
}
