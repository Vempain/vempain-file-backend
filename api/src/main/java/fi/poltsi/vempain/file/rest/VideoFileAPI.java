package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.VideoFileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Video file API", description = "API for accessing and managing video files")
public interface VideoFileAPI {
	String BASE_PATH = "/files/video";

	@Operation(summary = "Get all video files", description = "Retrieve video files with paging", tags = "Video file API")
	@Parameter(name = "page", description = "Page number (0-based)", example = "0")
	@Parameter(name = "size", description = "Number of items per page", example = "50")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Page of video files retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PagedResponse<VideoFileResponse>> findAll(
			@RequestParam(name = "page", defaultValue = "0") @PositiveOrZero int page,
			@RequestParam(name = "size", defaultValue = "50") @Positive int size
	);

	@Operation(summary = "Get video file by ID", description = "Retrieve specific video file by its unique identifier", tags = "Video file API")
	@Parameter(name = "id", description = "Video ID to be removed", example = "1")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific video file retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific video file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<VideoFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Remove video file by ID", description = "Remove specific video file by its unique identifier", tags = "Video file API")
	@Parameter(name = "id", description = "Video ID to be removed", example = "1")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific video file removed successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific video file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}
