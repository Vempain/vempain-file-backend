package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.admin.api.response.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "Data publish API", description = "API for generating and publishing CSV datasets to Vempain Admin")
public interface DataPublishAPI {
	String BASE_PATH = "/data-publish";

	@Operation(
			summary = "Generate and publish music dataset",
			description = "Generates a CSV dataset from all music files in the database and publishes it to the Vempain Admin data store",
			tags = "Data publish API"
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Music dataset published successfully",
						 content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
											schema = @Schema(implementation = DataResponse.class))),
			@ApiResponse(responseCode = "404", description = "No music files found", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(path = BASE_PATH + "/music", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<DataResponse> publishMusicDataset();

	@Operation(
			summary = "Generate and publish GPS time-series dataset for a directory",
			description = "Generates a CSV time-series dataset from images with GPS metadata in the specified directory path and publishes it to Vempain Admin",
			tags = "Data publish API"
	)
	@Parameter(name = "directoryPath", description = "URL-encoded relative directory path to scan for GPS-tagged images", example = "holidays_2024", required = true)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "GPS time-series dataset published successfully",
						 content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
											schema = @Schema(implementation = DataResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid directory path", content = @Content),
			@ApiResponse(responseCode = "404", description = "No GPS-tagged images found in directory", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(path = BASE_PATH + "/gps-timeseries/{directoryPath}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<DataResponse> publishGpsTimeSeries(@PathVariable("directoryPath") String directoryPath);
}
