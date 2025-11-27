package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.response.PublishAllFileGroupsResponse;
import fi.poltsi.vempain.file.api.response.PublishFileGroupResponse;
import fi.poltsi.vempain.file.api.response.PublishProgressResponse;
import fi.poltsi.vempain.file.rest.PublishAPI;
import fi.poltsi.vempain.file.service.PublishProgressStore;
import fi.poltsi.vempain.file.service.PublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PublishController implements PublishAPI {

	private final PublishService publishService;
	private final PublishProgressStore publishProgressStore;

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

	@Override
	public ResponseEntity<PublishAllFileGroupsResponse> publishAllFileGroups() {
		long scheduled = publishService.publishAllFileGroups();
		return ResponseEntity.accepted()
							 .body(new PublishAllFileGroupsResponse(scheduled));
	}

	@Override
	public ResponseEntity<PublishProgressResponse> getPublishProgress() {
		var resp = PublishProgressResponse.builder()
										  .totalGroups(publishProgressStore.getTotal())
										  .scheduled(publishProgressStore.getScheduled())
										  .started(publishProgressStore.getStarted())
										  .completed(publishProgressStore.getCompleted())
										  .failed(publishProgressStore.getFailed())
										  .perGroupStatus(publishProgressStore.getPerGroupStatus())
										  .lastUpdated(publishProgressStore.getLastUpdated())
										  .build();

		return ResponseEntity.ok(resp);
	}
}
