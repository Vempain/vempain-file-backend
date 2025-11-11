package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.request.LocationGuardRequest;
import fi.poltsi.vempain.file.api.response.LocationGuardResponse;
import fi.poltsi.vempain.file.api.response.LocationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Tag(name = "Location API", description = "API for accessing and managing location data and location guards")
public interface LocationAPI {
	String BASE_PATH = "/location";

	@Operation(summary = "Get location data by ID", description = "Retrieve specific location data by its unique identifier")
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<LocationResponse> getLocationById(@PathVariable long id);

	@Operation(summary = "Create a location guard", description = "Add a new location guard (circle or square)")
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(path = BASE_PATH + "/guard", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<LocationGuardResponse> addLocationGuard(@Valid @RequestBody LocationGuardRequest request);

	@Operation(summary = "Update a location guard", description = "Update existing location guard by providing its ID")
	@SecurityRequirement(name = "Bearer Authentication")
	@PutMapping(path = BASE_PATH + "/guard", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<LocationGuardResponse> updateLocationGuard(@Valid @RequestBody LocationGuardRequest request);

	@Operation(summary = "Delete a location guard", description = "Delete a location guard by ID")
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(path = BASE_PATH + "/guard/{id}")
	ResponseEntity<Void> deleteLocationGuard(@PathVariable long id);

	@Operation(summary = "List all location guards", description = "Fetch all existing location guards")
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/guards", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<List<LocationGuardResponse>> findAllLocationGuards();

	@Operation(summary = "Check if GPS location is guarded", description = "Returns true if the given GPS location (by ID) lies within any guard")
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/guarded/{gpsLocationId}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Boolean> isGuardedLocation(@PathVariable long gpsLocationId);
}
