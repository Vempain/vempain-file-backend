package fi.poltsi.vempain.file.rest;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import fi.poltsi.vempain.common.api.response.TaskAcceptedResponse;
import fi.poltsi.vempain.file.api.request.PublishAllFileGroupsRequest;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.request.PublishFileRequest;
import fi.poltsi.vempain.file.api.request.PublishFilesRequest;
import fi.poltsi.vempain.file.api.response.PublishFileGroupStatusResponse;
import fi.poltsi.vempain.file.api.response.PublishFileStatusResponse;
import fi.poltsi.vempain.file.api.response.PublishUnitResponse;
import fi.poltsi.vempain.file.api.response.PublishUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@Tag(name = "PublishAPI", description = "API for publishing files")
public interface PublishAPI {

    String BASE_PATH = "/publish";

    @Operation(summary = "Publish a single file",
            description = "Publishes one file to the admin backend as a site file, as a background task of one step. No gallery is created "
                    + "or modified on the admin side. The caller needs the modify privilege on the file. The optional acls list grants "
                    + "additional admin users (GET /publish/users) or units (GET /publish/units) privileges on the created site file.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Accepted, the work continues as a background task; follow it through GET /tasks/{task_id}",
                content = { @Content(schema = @Schema(implementation = TaskAcceptedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
        @ApiResponse(responseCode = "400", description = "Invalid request issued, for example an ACL entry without a user ID or privileges",
                content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
        @ApiResponse(responseCode = "403", description = "The caller may not modify the file", content = @Content),
        @ApiResponse(responseCode = "404", description = "No file found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping(path = BASE_PATH + "/file", consumes = "application/json", produces = "application/json")
    ResponseEntity<TaskAcceptedResponse> publishFile(@Valid @RequestBody PublishFileRequest request);

    @Operation(summary = "Publish selected files",
            description = "Publishes the selected files to the admin backend as one background task. Files without an export are reported as failed steps.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Accepted, the work continues as a background task",
                content = @Content(schema = @Schema(implementation = TaskAcceptedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE)),
        @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
        @ApiResponse(responseCode = "403", description = "The caller may not modify one of the files", content = @Content),
        @ApiResponse(responseCode = "404", description = "A file does not exist", content = @Content) })
    @SecurityRequirement(name = "******")
    @PostMapping(path = BASE_PATH + "/files", consumes = "application/json", produces = "application/json")
    ResponseEntity<TaskAcceptedResponse> publishFiles(@Valid @RequestBody PublishFilesRequest request);

    @Operation(summary = "Publish File Group",
            description = "Publishes every file of a group to the admin backend as a background task. "
                    + "The caller needs the modify privilege on all files of the group. The optional acls list grants additional "
                    + "admin users (GET /publish/users) or units (GET /publish/units) privileges on the created site files and gallery.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Accepted, the work continues as a background task; follow it through GET /tasks/{task_id}",
                content = { @Content(schema = @Schema(implementation = TaskAcceptedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
        @ApiResponse(responseCode = "400", description = "Invalid request issued, for example an ACL entry without a user ID or privileges",
                content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
        @ApiResponse(responseCode = "403", description = "The caller may not modify every file of the group", content = @Content),
        @ApiResponse(responseCode = "404", description = "No file group found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping(path = BASE_PATH + "/file-group", consumes = "application/json", produces = "application/json")
    ResponseEntity<TaskAcceptedResponse> publishFileGroup(@Valid @RequestBody PublishFileGroupRequest request);

    @Operation(summary = "Publish all File Groups",
            description = "Publishes every file group the caller may modify to the admin backend as one background task; total_steps is the number of groups. "
                    + "The optional body carries the acls list applied to every published group.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Accepted, the work continues as a background task; follow it through GET /tasks/{task_id}",
                content = { @Content(schema = @Schema(implementation = TaskAcceptedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
        @ApiResponse(responseCode = "400", description = "Invalid ACL entry", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping(path = BASE_PATH + "/all-file-groups", consumes = "application/json", produces = "application/json")
    ResponseEntity<TaskAcceptedResponse> publishAllFileGroups(@Valid @RequestBody(required = false) PublishAllFileGroupsRequest request);

    @Operation(summary = "List the admin users that can be granted privileges on published resources",
            description = "Fetched from the admin backend with this backend's service account. The admin and file user bases are separate: these "
                    + "are admin accounts, offered so that a publish can grant them access to the site files and gallery it creates.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of grantable admin users", content = {
            @Content(array = @ArraySchema(schema = @Schema(implementation = PublishUserResponse.class)), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
        @ApiResponse(responseCode = "502", description = "The admin backend did not answer", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping(path = BASE_PATH + "/users", produces = "application/json")
    ResponseEntity<List<PublishUserResponse>> listPublishUsers();

    @Operation(summary = "List the admin units that can be granted privileges on published resources",
            description = "Fetched from the admin backend with this backend's service account, like the users. These are admin units, never "
                    + "this backend's own units; a publish grants them access to the site files and gallery it creates.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of grantable admin units", content = {
            @Content(array = @ArraySchema(schema = @Schema(implementation = PublishUnitResponse.class)), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
        @ApiResponse(responseCode = "502", description = "The admin backend did not answer", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping(path = BASE_PATH + "/units", produces = "application/json")
    ResponseEntity<List<PublishUnitResponse>> listPublishUnits();

    @Operation(summary = "Publication state of a file",
            description = "Whether the file has been published to the admin backend before and, if so, the site file it became and the admin users and "
                    + "units that were granted privileges on it (in the shape of the acls list of POST /publish/file, so they can be shown and sent "
                    + "again). The file backend asks the admin backend; a site file that was deleted there answers published=false. The caller needs "
                    + "the modify privilege on the file, like for publishing it.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "The publication state",
                        content = {
                            @Content(schema = @Schema(implementation = PublishFileStatusResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
                @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
                @ApiResponse(responseCode = "403", description = "The caller may not modify the file", content = @Content),
                @ApiResponse(responseCode = "404", description = "No file found", content = @Content),
                @ApiResponse(responseCode = "502", description = "The admin backend did not answer", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping(path = BASE_PATH + "/file/{fileId}/status", produces = "application/json")
    ResponseEntity<PublishFileStatusResponse> getPublishFileStatus(@PathVariable("fileId") long fileId);

    @Operation(summary = "Publication state of a file group",
            description = "Whether the file group has been published to the admin backend before and, if so, the gallery it became, the site files the "
                    + "gallery holds and the admin users and units that were granted privileges on it (in the shape of the acls list of "
                    + "POST /publish/file-group). A gallery that was deleted in the admin backend answers published=false. The caller needs the modify "
                    + "privilege on every file of the group, like for publishing it.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "The publication state",
                        content = {
                            @Content(schema = @Schema(implementation = PublishFileGroupStatusResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
                @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
                @ApiResponse(responseCode = "403", description = "The caller may not modify every file of the group", content = @Content),
                @ApiResponse(responseCode = "404", description = "No file group found", content = @Content),
                @ApiResponse(responseCode = "502", description = "The admin backend did not answer", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping(path = BASE_PATH + "/file-group/{fileGroupId}/status", produces = "application/json")
    ResponseEntity<PublishFileGroupStatusResponse> getPublishFileGroupStatus(@PathVariable("fileGroupId") long fileGroupId);
}
