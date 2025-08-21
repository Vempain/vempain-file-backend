package fi.poltsi.vempain.file.api;

import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Validated
@Tag(name = "FileGroupAPI", description = "Operations for listing and retrieving file groups")
public interface FileGroupAPI {
	String BASE_PATH = "/file-groups";

	@Operation(summary = "List all file groups", tags = "FileGroupAPI")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved all file groups",
						 content = {@Content(array = @ArraySchema(schema = @Schema(implementation = List.class)),
											 mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "400", description = "Invalid request issued", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "404", description = "No file group found", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
	})
	@GetMapping(path = BASE_PATH, produces = "application/json")
	ResponseEntity<List<FileGroupResponse>> getFileGroups();

	@Operation(summary = "Get a file group by id", tags = "FileGroupAPI")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved all file groups",
						 content = {@Content(array = @ArraySchema(schema = @Schema(implementation = List.class)),
											 mediaType = MediaType.APPLICATION_JSON_VALUE)}),
			@ApiResponse(responseCode = "400", description = "Invalid request issued", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
			@ApiResponse(responseCode = "404", description = "No file group found", content = @Content),
			@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
	})
	@GetMapping(path = BASE_PATH + "/{id}", produces = "application/json")
	ResponseEntity<FileGroupResponse> getFileGroupById(@PathVariable("id") @Positive Long id);
}

