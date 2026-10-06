package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.request.CreateGpsTimeSeriesRequest;
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

@Tag(name = "Data publish API", description = "API for generating and publishing CSV datasets to Vempain Admin")
public interface DataPublishAPI {
	String BASE_PATH = "/data-publish";

	@Operation(
			summary = "Generate and publish music dataset",
			description = "Starts a background task that generates a CSV dataset from all music files and publishes it to the Vempain Admin data store; "
						  + "the finished task carries the admin DataResponse as result",
			tags = "Data publish API"
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "202", description = "Accepted, the work continues as a background task; follow it through GET /tasks/{task_id}",
						 content = {@Content(schema = @Schema(implementation = TaskAcceptedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "404", description = "No music files found", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(path = BASE_PATH + "/music", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<TaskAcceptedResponse> publishMusicDataset();

	@Operation(
			summary = "Generate and publish GPS time-series dataset from a file group",
			description = "Starts a background task that generates a CSV time-series dataset from the GPS-tagged images of the file group and publishes it "
						  + "to Vempain Admin; the finished task carries the admin DataResponse as result",
			tags = "Data publish API"
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "202", description = "Accepted, the work continues as a background task; follow it through GET /tasks/{task_id}",
						 content = {@Content(schema = @Schema(implementation = TaskAcceptedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "400", description = "Invalid request parameters", content = @Content),
			@ApiResponse(responseCode = "404", description = "No GPS-tagged images found in file group", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(path = BASE_PATH + "/gps-timeseries", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<TaskAcceptedResponse> publishGpsTimeSeries(@Valid @RequestBody CreateGpsTimeSeriesRequest request);
}
