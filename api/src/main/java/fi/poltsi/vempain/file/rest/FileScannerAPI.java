package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.api.response.TaskAcceptedResponse;
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

@Tag(name = "FileScanner API", description = "API for scanning files and managing file metadata")
public interface FileScannerAPI {
	String BASE_PATH = "/scan-files";

	@Operation(summary = "Scan directory for new files",
			   description = "Starts a background task that scans the given directories for new files and updates their metadata. "
							 + "The finished task carries a ScanResponses result.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "202", description = "Accepted, the work continues as a background task; follow it through GET /tasks/{task_id}",
						 content = {@Content(schema = @Schema(implementation = TaskAcceptedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "400", description = "Invalid request issued or the directory is outside the configured root", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(value = BASE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<TaskAcceptedResponse> scan(@Valid @RequestBody ScanRequest scanRequest);
}
