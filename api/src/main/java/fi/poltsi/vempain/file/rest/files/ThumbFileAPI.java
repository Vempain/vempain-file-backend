package fi.poltsi.vempain.file.rest.files;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.ThumbFileResponse;
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

@Tag(name = "Thumb file API", description = "API for accessing and managing thumbnail files")
public interface ThumbFileAPI {

	String BASE_PATH = "/files/thumb";

	@Operation(summary = "Get all thumbnail files", description = "Retrieve paged thumbnail files")
	@Parameter(name = "page", description = "0-based page number", example = "0")
	@Parameter(name = "size", description = "Page size", example = "50")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Thumbnail files page retrieved"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PagedResponse<ThumbFileResponse>> findAll(
			@RequestParam(name = "page", defaultValue = "0") @PositiveOrZero int page,
			@RequestParam(name = "size", defaultValue = "50") @Positive int size
	);

	@Operation(summary = "Get thumbnail file by id")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Thumbnail file retrieved"),
			@ApiResponse(responseCode = "404", description = "Not found"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ThumbFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Delete thumbnail file by id")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Deleted"),
			@ApiResponse(responseCode = "404", description = "Not found"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(path = BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}

