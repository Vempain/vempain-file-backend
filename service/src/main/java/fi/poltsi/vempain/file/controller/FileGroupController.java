package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.api.FileGroupAPI;
import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import fi.poltsi.vempain.file.service.FileGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FileGroupController implements FileGroupAPI {

	private final FileGroupService fileGroupService;

	@Override
	public ResponseEntity<List<FileGroupResponse>> getFileGroups() {
		var response = fileGroupService.getAll();
		return ResponseEntity.ok(response);
	}

	@Override
	public ResponseEntity<FileGroupResponse> getFileGroupById(Long id) {
		var response = fileGroupService.getById(id);
		return ResponseEntity.ok(response);
	}
}
