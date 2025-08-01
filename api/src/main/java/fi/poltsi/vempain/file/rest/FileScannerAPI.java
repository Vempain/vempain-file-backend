package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.api.response.ScanResponse;
import io.swagger.v3.oas.annotations.Operation;
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
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(value = BASE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ScanResponse> scan(@Valid @RequestBody ScanRequest scanRequest);
}
