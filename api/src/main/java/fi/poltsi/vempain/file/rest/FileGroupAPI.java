package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.request.FileGroupRequest;
import fi.poltsi.vempain.file.api.response.FileGroupListResponse;
import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Validated
@Tag(name = "FileGroupAPI", description = "Operations for listing and retrieving file groups")
public interface FileGroupAPI {
	String BASE_PATH = "/file-groups";

	@Operation(summary = "List all file groups (paged)", tags = "FileGroupAPI")
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
	@PostMapping(path = BASE_PATH + "/paged", produces = "application/json")
	ResponseEntity<PagedResponse<FileGroupListResponse>> getFileGroups(@Valid @RequestBody PagedRequest pagedRequest);

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

	@Operation(summary = "Create a new file group", description = "Creates a file group and associates given files to the group")
	@ApiResponse(responseCode = "201", description = "Created",
				 content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FileGroupResponse.class)))
	@PostMapping(value = BASE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<FileGroupResponse> addFileGroup(@Valid @RequestBody FileGroupRequest request);

	@Operation(summary = "Update an existing file group", description = "Updates group metadata and replaces file associations with the provided list")
	@ApiResponse(responseCode = "200", description = "Updated",
				 content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FileGroupResponse.class)))
	@PutMapping(value = BASE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<FileGroupResponse> updateFileGroup(@Valid @RequestBody FileGroupRequest request);
}
