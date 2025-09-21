package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.DocumentFileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "Document file API", description = "API for accessing and managing document files")
public interface DocumentFileAPI {
	String BASE_PATH = "/files/document";

	@Operation(summary = "Get all document files", description = "Retrieve full list of document files", tags = "Document file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "List of document files retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<List<DocumentFileResponse>> findAll();

	@Operation(summary = "Get document file by ID", description = "Retrieve specific document file by its unique identifier", tags = "Document file API")
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
