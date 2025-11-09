package fi.poltsi.vempain.file.rest.files;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.InteractiveFileResponse;
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

@Tag(name = "Interactive file API", description = "API for accessing and managing interactive files")
public interface InteractiveFileAPI {

	String BASE_PATH = "/files/interactive";

	@Operation(summary = "Get all interactive files", description = "Retrieve paged interactive files")
	@Parameter(name = "page", description = "0-based page number", example = "0")
	@Parameter(name = "size", description = "Page size", example = "50")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Interactive files page retrieved"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PagedResponse<InteractiveFileResponse>> findAll(
			@RequestParam(name = "page", defaultValue = "0") @PositiveOrZero int page,
			@RequestParam(name = "size", defaultValue = "50") @Positive int size
	);

	@Operation(summary = "Get interactive file by id")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Interactive file retrieved"),
			@ApiResponse(responseCode = "404", description = "Not found"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<InteractiveFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Delete interactive file by id")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Deleted"),
			@ApiResponse(responseCode = "404", description = "Not found"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(path = BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}

