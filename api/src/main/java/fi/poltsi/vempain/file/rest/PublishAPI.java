package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.response.PublishAllFileGroupsResponse;
import fi.poltsi.vempain.file.api.response.PublishFileGroupResponse;
import fi.poltsi.vempain.file.api.response.PublishProgressResponse;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Validated
@Tag(name = "PublishAPI", description = "API for publishing files")
public interface PublishAPI {
	String BASE_PATH = "/publish";

	@Operation(summary = "Publish File Group", description = "Publishes a group of files asynchronously and returns the count of files in the group.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Got list of components",
						 content = {@Content(array = @ArraySchema(schema = @Schema(implementation = PublishFileGroupResponse.class)),
											 mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "400", description = "Invalid request issued", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "404", description = "No file group found", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(path = BASE_PATH + "/file-group", consumes = "application/json", produces = "application/json")
	ResponseEntity<PublishFileGroupResponse> PublishFileGroup(@Valid @RequestBody PublishFileGroupRequest request);

	@Operation(summary = "Publish all File Groups",
			   description = "Triggers asynchronous publishing for all file groups and returns the number of groups scheduled.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "202", description = "Accepted, publishing started",
						 content = {@Content(schema = @Schema(implementation = PublishAllFileGroupsResponse.class),
											 mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/all-file-groups", produces = "application/json")
	ResponseEntity<PublishAllFileGroupsResponse> publishAllFileGroups();

	@Operation(summary = "Get publishing progress", description = "Get the current progress of an ongoing publish-all operation")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Current progress",
						 content = {@Content(schema = @Schema(implementation = PublishProgressResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/progress", produces = "application/json")
	ResponseEntity<PublishProgressResponse> getPublishProgress();
}
