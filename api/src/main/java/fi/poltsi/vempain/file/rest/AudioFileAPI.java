package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.AudioFileResponse;
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

@Tag(name = "Audio file API", description = "API for accessing and managing audio files")
public interface AudioFileAPI {
	String BASE_PATH = "/files/audio";

	@Operation(summary = "Get all audio files", description = "Retrieve full list of audio files", tags = "Audio file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "List of audio files retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<List<AudioFileResponse>> findAll();

	@Operation(summary = "Get audio file by ID", description = "Retrieve specific audio file by its unique identifier", tags = "Audio file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific audio file retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific audio file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<AudioFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Remove audio file by ID", description = "Remove specific audio file by its unique identifier", tags = "Audio file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific audio file removed successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific audio file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}
