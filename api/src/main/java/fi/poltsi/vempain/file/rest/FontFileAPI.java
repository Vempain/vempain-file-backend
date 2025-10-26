package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.FontFileResponse;
import fi.poltsi.vempain.file.api.response.PagedResponse;
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

@Tag(name = "Font file API", description = "API for accessing and managing font files")
public interface FontFileAPI {
	String BASE_PATH = "/files/font";

	@Operation(summary = "Get all font files", description = "Retrieve of font files with paging", tags = "Font file API")
	@Parameter(name = "page", description = "Page number (0-based)", example = "0")
	@Parameter(name = "size", description = "Number of items per page", example = "50")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Page of font files retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PagedResponse<FontFileResponse>> findAll(
			@RequestParam(name = "page", defaultValue = "0") @PositiveOrZero int page,
			@RequestParam(name = "size", defaultValue = "50") @Positive int size
	);

	@Operation(summary = "Get font file by ID", description = "Retrieve specific font file by its unique identifier", tags = "Font file API")
	@Parameter(name = "id", description = "Font ID to be fetched", example = "1")
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
	@Parameter(name = "id", description = "Font ID to be fetched", example = "1")
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
