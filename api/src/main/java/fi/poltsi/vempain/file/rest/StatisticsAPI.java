package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.FileStatisticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "StatisticsAPI", description = "Operations for retrieving file database statistics")
public interface StatisticsAPI {
	String BASE_PATH = "/statistics";

	@Operation(summary = "Get file database statistics", tags = "StatisticsAPI")
	@SecurityRequirement(name = "******")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<FileStatisticsResponse> getStatistics();
}
