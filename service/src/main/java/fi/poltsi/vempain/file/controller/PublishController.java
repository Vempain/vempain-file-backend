package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.common.api.response.TaskAcceptedResponse;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.rest.PublishAPI;
import fi.poltsi.vempain.file.service.PublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PublishController implements PublishAPI {

	private final PublishService publishService;

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
	public ResponseEntity<TaskAcceptedResponse> publishAllFileGroups() {
		return ResponseEntity.accepted()
							 .body(publishService.publishAllFileGroups()
												 .toAcceptedResponse());
	}
}
