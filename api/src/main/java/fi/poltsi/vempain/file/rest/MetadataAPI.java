package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.common.api.response.TaskAcceptedResponse;
import fi.poltsi.vempain.file.api.request.MetadataFilesRequest;
import fi.poltsi.vempain.file.api.request.MetadataSearchRequest;
import fi.poltsi.vempain.file.api.request.MetadataValueUpdateRequest;
import fi.poltsi.vempain.file.api.response.MetadataSummaryResponse;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Browse the metadata extracted from the files (the {@code metadata} table: one row per file, ExifTool group and key) and
 * rewrite a value across every file that carries it. Metadata rows have no ACL of their own; the file lists only show files the
 * caller may read and a rewrite requires the modify privilege on every affected file.
 */
@Tag(name = "Metadata API", description = "API for browsing and rewriting file metadata values")
public interface MetadataAPI {

	String BASE_PATH = "/metadata";

	@Operation(summary = "List distinct metadata values", description = "Distinct metadata group, key and value combinations with the number of "
																		+ "files carrying each one, filtered by group, key and/or value")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "A page of metadata rows",
						 content = {@Content(schema = @Schema(implementation = PagedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "400", description = "Invalid request issued", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(path = BASE_PATH + "/paged", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PagedResponse<MetadataSummaryResponse>> getMetadataPageable(@Valid @RequestBody MetadataSearchRequest request);

	@Operation(summary = "List the files carrying a metadata value", description = "Files the caller may read that carry the given group, key and "
																				   + "value, filtered by file name and/or type and sortable by file "
																				   + "name, type and size")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "A page of files",
						 content = {@Content(schema = @Schema(implementation = PagedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "400", description = "Invalid request issued", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(path = BASE_PATH + "/files/paged", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PagedResponse<FileResponse>> getFilesByMetadata(@Valid @RequestBody MetadataFilesRequest request);

	@Operation(summary = "Rewrite a metadata value across all files", description = "Writes the new value (or removes the entry when the new value "
																					+ "is empty) into every file carrying the given group, key and "
																					+ "current value as a background task; the caller needs the "
																					+ "modify privilege on all of those files")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "202", description = "Accepted, the work continues as a background task; follow it through GET /tasks/{task_id}",
						 content = {@Content(schema = @Schema(implementation = TaskAcceptedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "400", description = "Invalid request, a read-only metadata group or no file carries the value", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "403", description = "The caller may not modify every affected file", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(path = BASE_PATH + "/value", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<TaskAcceptedResponse> updateMetadataValue(@Valid @RequestBody MetadataValueUpdateRequest request);
}
