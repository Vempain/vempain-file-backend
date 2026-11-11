package fi.poltsi.vempain.file.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

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
import fi.poltsi.vempain.file.rest.McpReadAPI;
import fi.poltsi.vempain.file.security.ApiTokenAuthenticationFilter;
import fi.poltsi.vempain.file.service.McpReadService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + ApiTokenAuthenticationFilter.API_TOKEN_MCP_READ_AUTHORITY + "')")
public class McpReadController implements McpReadAPI {

    private final McpReadService mcpReadService;

    @Override
    public ResponseEntity<McpPagedResponse<FileResponse>> getFiles(int page, int size, String type, String search) {
        return ResponseEntity.ok(mcpReadService.files(page, size, type, search));
    }

    @Override
    public ResponseEntity<FileResponse> getFile(long id) {
        return ResponseEntity.ok(mcpReadService.file(id));
    }

    @Override
    public ResponseEntity<McpPagedResponse<FileGroupListResponse>> getFileGroups(int page, int size) {
        return ResponseEntity.ok(mcpReadService.fileGroups(page, size));
    }

    @Override
    public ResponseEntity<FileGroupResponse> getFileGroup(long id) {
        return ResponseEntity.ok(mcpReadService.fileGroup(id));
    }

    @Override
    public ResponseEntity<McpPagedResponse<TagResponse>> getTags(int page, int size) {
        return ResponseEntity.ok(mcpReadService.tags(page, size));
    }

    @Override
    public ResponseEntity<McpPagedResponse<LocationResponse>> getLocations(int page, int size) {
        return ResponseEntity.ok(mcpReadService.locations(page, size));
    }

    @Override
    public ResponseEntity<FileStatisticsResponse> getStatistics() {
        return ResponseEntity.ok(mcpReadService.statistics());
    }

    @Override
    public ResponseEntity<McpPagedResponse<UserResponse>> getUsers(int page, int size) {
        return ResponseEntity.ok(mcpReadService.users(page, size));
    }

    @Override
    public ResponseEntity<McpPagedResponse<UnitResponse>> getUnits(int page, int size) {
        return ResponseEntity.ok(mcpReadService.units(page, size));
    }

    @Override
    public ResponseEntity<McpPagedResponse<AclResponse>> getAcls(int page, int size) {
        return ResponseEntity.ok(mcpReadService.acls(page, size));
    }
}
