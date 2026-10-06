package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Validated
@Tag(name = "PublishAPI", description = "API for publishing files")
public interface PublishAPI {
	String BASE_PATH = "/publish";

	@Operation(summary = "Publish File Group",
			   description = "Publishes every file of a group to the admin backend as a background task. "
							 + "The caller needs the modify privilege on all files of the group.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "202", description = "Accepted, the work continues as a background task; follow it through GET /tasks/{task_id}",
						 content = {@Content(schema = @Schema(implementation = TaskAcceptedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "400", description = "Invalid request issued", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "403", description = "The caller may not modify every file of the group", content = @Content),
			@ApiResponse(responseCode = "404", description = "No file group found", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@PostMapping(path = BASE_PATH + "/file-group", consumes = "application/json", produces = "application/json")
	ResponseEntity<TaskAcceptedResponse> publishFileGroup(@Valid @RequestBody PublishFileGroupRequest request);

	@Operation(summary = "Publish all File Groups",
			   description = "Publishes every file group the caller may modify to the admin backend as one background task; total_steps is the number of groups.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "202", description = "Accepted, the work continues as a background task; follow it through GET /tasks/{task_id}",
						 content = {@Content(schema = @Schema(implementation = TaskAcceptedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/all-file-groups", produces = "application/json")
	ResponseEntity<TaskAcceptedResponse> publishAllFileGroups();

}
