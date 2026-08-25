package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.request.TagOperationRequest;
import fi.poltsi.vempain.file.api.request.TagRequest;
import fi.poltsi.vempain.file.api.response.TagResponse;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Tag(name = "Tag API", description = "API for managing tags")
public interface TagAPI {

	String BASE_PATH = "/tags";

	@Operation(summary = "Get all tags", description = "Retrieve a list of all available tags")
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(value = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<List<TagResponse>> getAllTags();

	@Operation(summary = "Get a tag by ID", description = "Retrieve a specific tag by its unique identifier")
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<TagResponse> getTagById(@PathVariable Long id);

	@Operation(summary = "Get files for a tag", description = "Retrieve files associated with a tag using paging, sorting and search")
	@SecurityRequirement(name = "******")
	@PostMapping(path = BASE_PATH + "/{id}/files/paged", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PagedResponse<FileResponse>> getFilesByTag(@PathVariable Long id, @Valid @RequestBody PagedRequest pagedRequest);

	@Operation(summary = "Create a new tag", description = "Add a new tag to the system")
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(value = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<TagResponse> createTag(@Valid @RequestBody TagRequest tagRequest);

	@Operation(summary = "Update a tag", description = "Update the details of an existing tag")
	@SecurityRequirement(name = "Bearer Authentication")
	@PutMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<TagResponse> updateTag(@Valid @RequestBody TagRequest tagRequest
	);

	@Operation(summary = "Delete a tag", description = "Remove a tag from the system")
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(path = BASE_PATH + "/{id}")
	ResponseEntity<Void> deleteTag(@PathVariable(name = "id") Long id);

	@PostMapping(path = BASE_PATH + "/files/add", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Void> addTag(@Valid @RequestBody TagOperationRequest request);

	@PostMapping(path = BASE_PATH + "/files/remove", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Void> removeTag(@Valid @RequestBody TagOperationRequest request);

	@PostMapping(path = BASE_PATH + "/files/replace", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Void> replaceTag(@Valid @RequestBody TagOperationRequest request);

	@PostMapping(path = BASE_PATH + "/files/rename", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Void> renameTag(@Valid @RequestBody TagOperationRequest request);

	@PostMapping(path = BASE_PATH + "/all/remove", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Void> removeTagFromAll(@Valid @RequestBody TagOperationRequest request);

	@PostMapping(path = BASE_PATH + "/all/replace", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Void> replaceTagAcrossAll(@Valid @RequestBody TagOperationRequest request);

	@PostMapping(path = BASE_PATH + "/all/rename", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Void> renameTagAcrossAll(@Valid @RequestBody TagOperationRequest request);
}
