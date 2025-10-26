package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.DocumentFileResponse;
import fi.poltsi.vempain.file.api.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Document file API", description = "API for accessing and managing document files")
public interface DocumentFileAPI {
	String BASE_PATH = "/files/document";

	@Operation(summary = "Get all document files", description = "Retrieve of document files with paging", tags = "Document file API")
	@Parameter(name = "page", description = "Page number (0-based)", example = "0")
	@Parameter(name = "size", description = "Number of items per page", example = "50")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Page of document files retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PagedResponse<DocumentFileResponse>> findAll(
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "50") int size
	);

	@Operation(summary = "Get document file by ID", description = "Retrieve specific document file by its unique identifier", tags = "Document file API")
	@Parameter(name = "id", description = "Document ID to be fetched", example = "1")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific document file retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific document file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<DocumentFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Remove document file by ID", description = "Remove specific document file by its unique identifier", tags = "Document file API")
	@Parameter(name = "id", description = "Document ID to be fetched", example = "1")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific document file removed successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific document file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}
