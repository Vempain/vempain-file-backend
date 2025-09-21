package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.FontFileResponse;
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

@Tag(name = "Font file API", description = "API for accessing and managing font files")
public interface FontFileAPI {
	String BASE_PATH = "/files/font";

	@Operation(summary = "Get all font files", description = "Retrieve full list of font files", tags = "Font file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "List of font files retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<List<FontFileResponse>> findAll();

	@Operation(summary = "Get font file by ID", description = "Retrieve specific font file by its unique identifier", tags = "Font file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific font file retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific font file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<FontFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Remove font file by ID", description = "Remove specific font file by its unique identifier", tags = "Font file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific font file removed successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific font file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}
