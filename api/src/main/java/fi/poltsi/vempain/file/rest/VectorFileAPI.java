package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.VectorFileResponse;
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

@Tag(name = "Vector file API", description = "API for accessing and managing vector files")
public interface VectorFileAPI {
	String BASE_PATH = "/files/vector";

	@Operation(summary = "Get all vector files", description = "Retrieve full list of vector files", tags = "Vector file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "List of vector files retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<List<VectorFileResponse>> findAll();

	@Operation(summary = "Get vector file by ID", description = "Retrieve specific vector file by its unique identifier", tags = "Vector file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific vector file retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific vector file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<VectorFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Remove vector file by ID", description = "Remove specific vector file by its unique identifier", tags = "Vector file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific vector file removed successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific vector file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}
