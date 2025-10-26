package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.api.response.FileGroupListResponse;
import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.rest.FileGroupAPI;
import fi.poltsi.vempain.file.service.FileGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FileGroupController implements FileGroupAPI {

	private final FileGroupService fileGroupService;

	@Override
	public ResponseEntity<PagedResponse<FileGroupListResponse>> getFileGroups(int page, int size) {
		var response = fileGroupService.getAll(page, size);
		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<FileGroupResponse> getFileGroupById(Long id) {
		var response = fileGroupService.getById(id);
		return ResponseEntity.ok(response);
	}
}
