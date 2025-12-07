package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.request.FileGroupRequest;
import fi.poltsi.vempain.file.api.response.FileGroupListResponse;
import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import fi.poltsi.vempain.file.rest.FileGroupAPI;
import fi.poltsi.vempain.file.service.FileGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class FileGroupController implements FileGroupAPI {

	private final FileGroupService fileGroupService;

	@Override
	public ResponseEntity<PagedResponse<FileGroupListResponse>> getFileGroups(PagedRequest pagedRequest) {
		var response = fileGroupService.getAll(pagedRequest);
		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<FileGroupResponse> getFileGroupById(Long id) {
		var response = fileGroupService.getById(id);
		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<FileGroupResponse> addFileGroup(@Valid FileGroupRequest request) {
		FileGroupResponse created = fileGroupService.addFileGroup(request);
		return ResponseEntity.created(URI.create("/api/file-groups/" + created.getId()))
							 .body(created);
	}

	@Override
	public ResponseEntity<FileGroupResponse> updateFileGroup(@Valid FileGroupRequest request) {
		FileGroupResponse updated = fileGroupService.updateFileGroup(request);
		return ResponseEntity.ok(updated);
	}
}
