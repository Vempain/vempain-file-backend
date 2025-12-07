package fi.poltsi.vempain.file.rest.files;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.BinaryFileResponse;
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

@Tag(name = "Binary file API", description = "API for accessing and managing binary files")
public interface BinaryFileAPI {
	String BASE_PATH = "/files/binary";

	@Operation(summary = "Get all binary files", description = "Retrieve binary files with paging", tags = "Binary file API")
	@Parameter(name = "page", description = "Page number (0-based)", example = "0")
	@Parameter(name = "size", description = "Number of items per page", example = "50")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Page of binary files retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PagedResponse<BinaryFileResponse>> findAll(
			@RequestParam(name = "page", defaultValue = "0") @PositiveOrZero int page,
			@RequestParam(name = "size", defaultValue = "50") @Positive int size
	);

	@Operation(summary = "Get binary file by ID", description = "Retrieve specific binary file by its unique identifier", tags = "Binary file API")
	@Parameter(name = "id", description = "Binary file ID", example = "1")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Binary file retrieved successfully"),
			@ApiResponse(responseCode = "404", description = "Binary file not found"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<BinaryFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Remove binary file by ID", description = "Delete a binary file", tags = "Binary file API")
	@Parameter(name = "id", description = "Binary file ID", example = "1")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Binary file removed successfully"),
			@ApiResponse(responseCode = "404", description = "Binary file not found"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}

