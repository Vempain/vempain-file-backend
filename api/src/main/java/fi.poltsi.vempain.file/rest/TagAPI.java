package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.request.TagRequest;
import fi.poltsi.vempain.file.api.response.TagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Tag(name = "Tag API", description = "API for managing tags")
@RequestMapping("/api/tags")
public interface TagAPI {

	@Operation(summary = "Get all tags", description = "Retrieve a list of all available tags")
	@GetMapping
	ResponseEntity<List<TagResponse>> getAllTags();

	@Operation(summary = "Get a tag by ID", description = "Retrieve a specific tag by its unique identifier")
	@GetMapping("/{id}")
	ResponseEntity<TagResponse> getTagById(@PathVariable Long id);

	@Operation(summary = "Create a new tag", description = "Add a new tag to the system")
	@PostMapping
	ResponseEntity<TagResponse> createTag(@Valid @RequestBody TagRequest tagRequest);

	@Operation(summary = "Update a tag", description = "Update the details of an existing tag")
	@PutMapping("/{id}")
	ResponseEntity<TagResponse> updateTag(
			@PathVariable Long id,
			@Valid @RequestBody TagRequest tagRequest
	);

	@Operation(summary = "Delete a tag", description = "Remove a tag from the system")
	@DeleteMapping("/{id}")
	ResponseEntity<Void> deleteTag(@PathVariable Long id);
}
