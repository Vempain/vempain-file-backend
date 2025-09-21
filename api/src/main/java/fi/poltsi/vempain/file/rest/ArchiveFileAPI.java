package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.ArchiveFileResponse;
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

@Tag(name = "Archive file API", description = "API for accessing and managing archive files")
public interface ArchiveFileAPI {
	String BASE_PATH = "/files/archive";

	@Operation(summary = "Get all archive files", description = "Retrieve full list of archive files", tags = "Archive file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "List of archive files retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<List<ArchiveFileResponse>> findAll();

	@Operation(summary = "Get archive file by ID", description = "Retrieve specific archive file by its unique identifier", tags = "Archive file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific archive file retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific archive file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ArchiveFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Remove archive file by ID", description = "Remove specific archive file by its unique identifier", tags = "Archive file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific archive file removed successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific archive file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}
