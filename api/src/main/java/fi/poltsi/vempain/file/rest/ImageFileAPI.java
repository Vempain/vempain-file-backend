package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.ImageFileResponse;
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

@Tag(name = "Image file API", description = "API for accessing and managing image files")
public interface ImageFileAPI {
	String BASE_PATH = "/files/image";

	@Operation(summary = "Get all image files", description = "Retrieve full list of image files", tags = "Image file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "List of image files retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<List<ImageFileResponse>> findAll();

	@Operation(summary = "Get image file by ID", description = "Retrieve specific image file by its unique identifier", tags = "Image file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific image file retrieved successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific image file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ImageFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Remove image file by ID", description = "Remove specific image file by its unique identifier", tags = "Image file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Specific image file removed successfully"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "404", description = "Specific image file not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}
