package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.FileGroupListResponse;
import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import fi.poltsi.vempain.file.api.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
@Tag(name = "FileGroupAPI", description = "Operations for listing and retrieving file groups")
public interface FileGroupAPI {
	String BASE_PATH = "/file-groups";

	@Operation(summary = "List all file groups (paged)", tags = "FileGroupAPI")
	@Parameter(name = "page", description = "Page number (0-based)", example = "0")
	@Parameter(name = "size", description = "Number of items per page", example = "50")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved all file groups",
						 content = {@Content(schema = @Schema(implementation = PagedResponse.class),
											 mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "400", description = "Invalid request issued", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "404", description = "No file group found", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = "application/json")
	ResponseEntity<PagedResponse<FileGroupListResponse>> getFileGroups(
			@RequestParam(name = "page", defaultValue = "0") @PositiveOrZero int page,
			@RequestParam(name = "size", defaultValue = "50") @Positive int size
	);

	@Operation(summary = "Get a file group by id", tags = "FileGroupAPI")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved all file groups",
						 content = {@Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = java.util.List.class)),
											 mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "400", description = "Invalid request issued", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "404", description = "No file group found", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = "application/json")
	ResponseEntity<FileGroupResponse> getFileGroupById(@PathVariable("id") @Positive Long id);
}
