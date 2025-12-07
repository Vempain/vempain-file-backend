package fi.poltsi.vempain.file.rest.files;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.ExecutableFileResponse;
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

@Tag(name = "Executable file API", description = "API for accessing and managing executable files")
public interface ExecutableFileAPI {
	String BASE_PATH = "/files/executable";

	@Operation(summary = "Get all executable files", description = "Retrieve executable files with paging", tags = "Executable file API")
	@Parameter(name = "page", description = "Page number (0-based)", example = "0")
	@Parameter(name = "size", description = "Items per page", example = "50")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Page retrieved"),
			@ApiResponse(responseCode = "403", description = "Unauthorized")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PagedResponse<ExecutableFileResponse>> findAll(
			@RequestParam(name = "page", defaultValue = "0") @PositiveOrZero int page,
			@RequestParam(name = "size", defaultValue = "50") @Positive int size
	);

	@Operation(summary = "Get executable file by ID", description = "Retrieve executable file by ID", tags = "Executable file API")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Executable file retrieved"),
			@ApiResponse(responseCode = "404", description = "Executable file not found"),
			@ApiResponse(responseCode = "403", description = "Unauthorized")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ExecutableFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Remove executable file", description = "Delete executable file by ID", tags = "Executable file API")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Executable file removed"),
			@ApiResponse(responseCode = "404", description = "Executable file not found"),
			@ApiResponse(responseCode = "403", description = "Unauthorized")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}
