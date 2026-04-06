package fi.poltsi.vempain.file.rest.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.DataFileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Data file API", description = "API for accessing and managing data files")
public interface DataFileAPI {
	String BASE_PATH = "/files/data";

	@Operation(summary = "Get all data files", description = "Retrieve data files with paging, sorting and search", tags = "Data file API")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Page of data files retrieved successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid request"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(path = BASE_PATH + "/paged", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PagedResponse<DataFileResponse>> findAll(@Valid @RequestBody PagedRequest pagedRequest);

	@Operation(summary = "Get data file by ID", description = "Retrieve specific data file by ID", tags = "Data file API")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Data file retrieved"),
			@ApiResponse(responseCode = "404", description = "Data file not found"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<DataFileResponse> findById(@PathVariable("id") long id);

	@Operation(summary = "Remove data file by ID", description = "Delete a data file", tags = "Data file API")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Data file removed"),
			@ApiResponse(responseCode = "404", description = "Data file not found"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(BASE_PATH + "/{id}")
	ResponseEntity<Void> delete(@PathVariable("id") long id);
}
