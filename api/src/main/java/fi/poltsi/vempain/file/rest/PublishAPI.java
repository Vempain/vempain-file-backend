package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.response.PublishFileGroupResponse;
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
}
