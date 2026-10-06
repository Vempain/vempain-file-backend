package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.TaskProgressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Progress callback API for background tasks. Every long-running endpoint answers 202 with a {@code TaskAcceptedResponse}; the
 * client polls {@code GET /tasks/{task_id}} until the status is COMPLETED or FAILED and then dismisses the task with DELETE.
 * Tasks are private to the user who started them.
 */
@Tag(name = "TaskAPI", description = "Progress of background tasks started by the current user")
public interface TaskAPI {
	String BASE_PATH = "/tasks";

	@Operation(summary = "List my tasks", description = "Returns the running and recently finished tasks of the current user, newest first")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Task list",
						 content = {@Content(array = @ArraySchema(schema = @Schema(implementation = TaskProgressResponse.class)),
											 mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<List<TaskProgressResponse>> getTasks();

	@Operation(summary = "Get task progress", description = "Returns the progress of one task of the current user")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Task progress",
						 content = {@Content(schema = @Schema(implementation = TaskProgressResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "404", description = "Unknown task or task of another user", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@GetMapping(path = BASE_PATH + "/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<TaskProgressResponse> getTask(@PathVariable("taskId") String taskId);

	@Operation(summary = "Dismiss a finished task", description = "Removes a completed or failed task from the list of the current user")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "Task dismissed", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "404", description = "Unknown task or task of another user", content = @Content),
			@ApiResponse(responseCode = "409", description = "The task is still running", content = @Content)
	})
	@SecurityRequirement(name = "Bearer Authentication")
	@DeleteMapping(path = BASE_PATH + "/{taskId}")
	ResponseEntity<Void> dismissTask(@PathVariable("taskId") String taskId);
}
