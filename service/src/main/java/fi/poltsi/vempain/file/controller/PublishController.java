package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.common.api.response.TaskAcceptedResponse;
import fi.poltsi.vempain.file.api.request.PublishAllFileGroupsRequest;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.request.PublishFileRequest;
import fi.poltsi.vempain.file.api.response.PublishUserResponse;
import fi.poltsi.vempain.file.rest.PublishAPI;
import fi.poltsi.vempain.file.service.PublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PublishController implements PublishAPI {

	private final PublishService publishService;

	@Override
	public ResponseEntity<TaskAcceptedResponse> publishFile(PublishFileRequest request) {
		// 404 when the file does not exist, 403 when the caller may not modify it; both are decided before the task starts
		publishService.authorizeFilePublish(request.getFileId());
		return ResponseEntity.accepted()
							 .body(publishService.publishFile(request)
												 .toAcceptedResponse());
	}

	@Override
	public ResponseEntity<TaskAcceptedResponse> publishFileGroup(PublishFileGroupRequest request) {
		if (publishService.countFilesInGroup(request.getFileGroupId()) == 0L) {
			return ResponseEntity.notFound()
			                     .build();
		}

		publishService.authorizeFileGroupPublish(request.getFileGroupId());
		return ResponseEntity.accepted()
							 .body(publishService.publishFileGroup(request)
												 .toAcceptedResponse());
	}

	@Override
	public ResponseEntity<TaskAcceptedResponse> publishAllFileGroups(PublishAllFileGroupsRequest request) {
		return ResponseEntity.accepted()
							 .body(publishService.publishAllFileGroups(request)
												 .toAcceptedResponse());
	}

	@Override
	public ResponseEntity<List<PublishUserResponse>> listPublishUsers() {
		return ResponseEntity.ok(publishService.listPublishUsers());
	}
}
