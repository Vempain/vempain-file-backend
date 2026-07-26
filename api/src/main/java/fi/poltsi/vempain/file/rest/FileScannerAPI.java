package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.api.response.ScanResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
	           description = "Initiates a scan of the specified directory to find new files and update their metadata")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Got list of components",
			             content = {@Content(array = @ArraySchema(schema = @Schema(implementation = ScanResponses.class)),
			                                 mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "400", description = "Invalid request issued", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "404", description = "No directory found", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(value = BASE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ScanResponses> scan(@Valid @RequestBody ScanRequest scanRequest);
}
