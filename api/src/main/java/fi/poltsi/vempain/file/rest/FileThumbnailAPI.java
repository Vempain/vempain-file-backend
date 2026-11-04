package fi.poltsi.vempain.file.rest;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import fi.poltsi.vempain.file.api.response.files.FileThumbnailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "File Thumbnail API", description = "API for accessing and removing file thumbnail sub-resources")
public interface FileThumbnailAPI {

    String BASE_PATH = "/files";

    @Operation(summary = "Get a file thumbnail", description = "Streams the generated thumbnail of a file")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thumbnail returned"),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content),
        @ApiResponse(responseCode = "404", description = "File or thumbnail not found", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping(path = BASE_PATH + "/{id}/thumbnail")
    ResponseEntity<Resource> getThumbnail(@PathVariable("id") long id);

    @Operation(summary = "Get thumbnail metadata")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thumbnail metadata returned"),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content),
        @ApiResponse(responseCode = "404", description = "File or thumbnail not found", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping(path = BASE_PATH + "/{id}/thumbnail/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FileThumbnailResponse> getThumbnailMetadata(@PathVariable("id") long id);

    @Operation(summary = "Delete a file thumbnail")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Thumbnail deleted"),
        @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content),
        @ApiResponse(responseCode = "404", description = "File or thumbnail not found", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping(path = BASE_PATH + "/{id}/thumbnail")
    ResponseEntity<Void> deleteThumbnail(@PathVariable("id") long id);
}
