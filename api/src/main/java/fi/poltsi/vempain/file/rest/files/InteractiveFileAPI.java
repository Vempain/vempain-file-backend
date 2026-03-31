package fi.poltsi.vempain.file.rest.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.InteractiveFileResponse;
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

@Tag(name = "Interactive file API", description = "API for accessing and managing interactive files")
public interface InteractiveFileAPI {

	String BASE_PATH = "/files/interactive";

	@Operation(summary = "Get all interactive file files",
	           description = "Retrieve interactive file files with paging, sorting and search",
	           tags = "Interactive file API")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Page of interactive file files retrieved successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid request"),
			@ApiResponse(responseCode = "403", description = "Unauthorized access"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(path = BASE_PATH + "/paged", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PagedResponse<InteractiveFileResponse>> findAll(@Valid @RequestBody PagedRequest pagedRequest);

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

