package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.response.PublishFileGroupResponse;
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
	public ResponseEntity<PublishFileGroupResponse> PublishFileGroup(PublishFileGroupRequest request) {
		var count = publishService.countFilesInGroup(request.getFileGroupId());

		if (count == 0L) {
			return ResponseEntity.notFound()
								 .build();
		}

		publishService.publishFileGroup(request);
		return ResponseEntity.accepted()
							 .body(new PublishFileGroupResponse(count));
	}
}

