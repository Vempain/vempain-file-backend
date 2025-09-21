package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.IconFileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "Icon file API", description = "API for accessing and managing icon files")
public interface IconFileAPI {
	String BASE_PATH = "/files/icon";

	@Operation(summary = "Get all icon files", description = "Retrieve full list of icon files", tags = "Icon file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "List of icon files retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<List<IconFileResponse>> findAll();

	@Operation(summary = "Get icon file by ID", description = "Retrieve specific icon file by its unique identifier", tags = "Icon file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific icon file retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific icon file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<IconFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Remove icon file by ID", description = "Remove specific icon file by its unique identifier", tags = "Icon file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific icon file removed successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific icon file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}
