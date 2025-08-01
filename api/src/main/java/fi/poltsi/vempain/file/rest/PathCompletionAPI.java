package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.request.PathCompletionRequest;
import fi.poltsi.vempain.file.api.response.PathCompletionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Path Completion API", description = "API for completing directory paths")
public interface PathCompletionAPI {

	String BASE_PATH = "/path-completion";

	@Operation(summary = "Complete a path", description = "Returns possible completions for a directory path")
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(value = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<PathCompletionResponse> completePath(@Valid @RequestBody PathCompletionRequest request);
}
