package fi.poltsi.vempain.file.rest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import fi.poltsi.vempain.auth.api.response.AclResponse;
import fi.poltsi.vempain.auth.api.response.UnitResponse;
import fi.poltsi.vempain.auth.api.response.UserResponse;
import fi.poltsi.vempain.common.api.response.LocationResponse;
import fi.poltsi.vempain.file.api.response.FileGroupListResponse;
import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import fi.poltsi.vempain.file.api.response.FileStatisticsResponse;
import fi.poltsi.vempain.file.api.response.McpPagedResponse;
import fi.poltsi.vempain.file.api.response.TagResponse;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only, system-wide (never ACL-filtered) view of the file backend for vempain-mcp. Only reachable with an {@code MCP_READ} API token in the
 * {@link ApiTokenAPI#API_TOKEN_HEADER} header and only with GET; a JWT session is refused. Pages: {@code page} from 0, {@code size} 1..500 (default 100),
 * anything else answers 400.
 */
@Tag(name = "McpReadAPI", description = "Read-only system-wide access for vempain-mcp")
public interface McpReadAPI {

    String BASE_PATH = "/mcp";

    @Operation(summary = "List the files", description = "Ordered by id; optional file type and a file name/path search", tags = "McpReadAPI")
    @Parameter(name = "type", description = "File type (FileTypeEnum), e.g. IMAGE", example = "IMAGE")
    @Parameter(name = "search", description = "Text contained in the file name or path", example = "holiday")
    @GetMapping(value = BASE_PATH + "/files", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<McpPagedResponse<FileResponse>> getFiles(@RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size, @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "search", required = false) String search);

    @Operation(summary = "One file with its metadata", tags = "McpReadAPI")
    @GetMapping(value = BASE_PATH + "/files/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FileResponse> getFile(@PathVariable("id") long id);

    @Operation(summary = "List the file groups", tags = "McpReadAPI")
    @GetMapping(value = BASE_PATH + "/file-groups", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<McpPagedResponse<FileGroupListResponse>> getFileGroups(@RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size);

    @Operation(summary = "One file group with its files", tags = "McpReadAPI")
    @GetMapping(value = BASE_PATH + "/file-groups/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FileGroupResponse> getFileGroup(@PathVariable("id") long id);

    @Operation(summary = "List the tags", tags = "McpReadAPI")
    @GetMapping(value = BASE_PATH + "/tags", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<McpPagedResponse<TagResponse>> getTags(@RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size);

    @Operation(summary = "List the GPS locations", tags = "McpReadAPI")
    @GetMapping(value = BASE_PATH + "/locations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<McpPagedResponse<LocationResponse>> getLocations(@RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size);

    @Operation(summary = "File database statistics", tags = "McpReadAPI")
    @GetMapping(value = BASE_PATH + "/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<FileStatisticsResponse> getStatistics();

    @Operation(summary = "List the users of the file backend", tags = "McpReadAPI")
    @GetMapping(value = BASE_PATH + "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<McpPagedResponse<UserResponse>> getUsers(@RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size);

    @Operation(summary = "List the units of the file backend", tags = "McpReadAPI")
    @GetMapping(value = BASE_PATH + "/units", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<McpPagedResponse<UnitResponse>> getUnits(@RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size);

    @Operation(summary = "List the ACL rows of the file backend", tags = "McpReadAPI")
    @GetMapping(value = BASE_PATH + "/acls", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<McpPagedResponse<AclResponse>> getAcls(@RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size);
}
