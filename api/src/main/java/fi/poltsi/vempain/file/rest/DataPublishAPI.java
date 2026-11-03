package fi.poltsi.vempain.file.rest;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.common.api.response.TaskAcceptedResponse;
import fi.poltsi.vempain.file.api.request.CreateGpsTimeSeriesRequest;
import fi.poltsi.vempain.file.api.request.DataSetPagedRequest;
import fi.poltsi.vempain.file.api.request.PublishMusicDatasetRequest;
import fi.poltsi.vempain.file.api.response.DataSetSummaryResponse;
import fi.poltsi.vempain.file.api.response.PublishDataSetStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Data publish API", description = "API for generating and publishing CSV datasets to Vempain Admin")
public interface DataPublishAPI {

    String BASE_PATH = "/data-publish";

    @Operation(summary = "Generate and publish music dataset",
            description = "Starts a background task that generates a CSV dataset from all music files and publishes it to the Vempain Admin data store; "
                    + "the finished task carries the admin DataResponse as result. The optional body's publish_to_site also starts the admin "
                    + "backend's publishing of the data set to the website as the last step, and its acls name the admin users and units granted "
                    + "privileges on the data set (see GET /data-publish/{identifier}/status for the current ones)",
            tags = "Data publish API")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Accepted, the work continues as a background task; follow it through GET /tasks/{task_id}",
                content = { @Content(schema = @Schema(implementation = TaskAcceptedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
        @ApiResponse(responseCode = "404", description = "No music files found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping(path = BASE_PATH + "/music", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TaskAcceptedResponse> publishMusicDataset(@Valid @RequestBody(required = false) PublishMusicDatasetRequest request);

    @Operation(summary = "Generate and publish GPS time-series dataset from a file group",
            description = "Starts a background task that generates a CSV time-series dataset from the GPS-tagged images of the file group and publishes it "
                    + "to Vempain Admin; the finished task carries the admin DataResponse as result. publish_to_site also starts the admin backend's "
                    + "publishing of the data set to the website as the last step, acls names the admin users and units granted privileges on it",
            tags = "Data publish API")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Accepted, the work continues as a background task; follow it through GET /tasks/{task_id}",
                content = { @Content(schema = @Schema(implementation = TaskAcceptedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters", content = @Content),
        @ApiResponse(responseCode = "404", description = "No GPS-tagged images found in file group", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping(path = BASE_PATH + "/gps-timeseries", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TaskAcceptedResponse> publishGpsTimeSeries(@Valid @RequestBody CreateGpsTimeSeriesRequest request);

    @Operation(summary = "List the data sets of the admin backend page by page",
            description = "The data sets this backend created in the admin backend (music library, GPS time series, ...), filtered, sorted and paged by "
                    + "the admin backend (POST /content-management/data/paged): search over identifier, type and description or the one "
                    + "filter_column, type, modified_after_publish, sort_by identifier|type|description|generated|published|created|modified|creator|"
                    + "modifier|acl_id|id. The admin backend lists what the service account may read.",
            tags = "Data publish API")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "A page of data sets",
                content = { @Content(schema = @Schema(implementation = PagedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
        @ApiResponse(responseCode = "400", description = "Invalid paging parameters", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
        @ApiResponse(responseCode = "502", description = "The admin backend did not answer", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping(path = BASE_PATH + "/paged", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PagedResponse<DataSetSummaryResponse>> getPagedDataSets(@Valid @RequestBody DataSetPagedRequest request);

    @Operation(summary = "Publication state of a data set",
            description = "Whether the admin backend holds a data set with the identifier already (music_library, or the identifier derived from a GPS time "
                    + "series name) and the admin users and units that were granted privileges on it, in the shape of the acls list of the create "
                    + "requests, so that the dialog can show and resend them. The file backend asks the admin backend.",
            tags = "Data publish API")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "The publication state",
                        content = {
                            @Content(schema = @Schema(implementation = PublishDataSetStatusResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
                @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
                @ApiResponse(responseCode = "502", description = "The admin backend did not answer", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping(path = BASE_PATH + "/{identifier}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PublishDataSetStatusResponse> getDataSetStatus(@PathVariable("identifier") String identifier);
}
