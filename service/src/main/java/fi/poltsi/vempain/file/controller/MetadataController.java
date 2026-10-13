package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.common.api.response.TaskAcceptedResponse;
import fi.poltsi.vempain.file.api.request.MetadataFilesRequest;
import fi.poltsi.vempain.file.api.request.MetadataSearchRequest;
import fi.poltsi.vempain.file.api.request.MetadataValueUpdateRequest;
import fi.poltsi.vempain.file.api.response.MetadataSummaryResponse;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.rest.MetadataAPI;
import fi.poltsi.vempain.file.service.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MetadataController implements MetadataAPI {

	private final MetadataService metadataService;

	@Override
	public ResponseEntity<PagedResponse<MetadataSummaryResponse>> getMetadataPageable(MetadataSearchRequest request) {
		return ResponseEntity.ok(metadataService.findSummaries(request));
	}

	@Override
	public ResponseEntity<PagedResponse<FileResponse>> getFilesByMetadata(MetadataFilesRequest request) {
		return ResponseEntity.ok(metadataService.findFiles(request));
	}

	@Override
	public ResponseEntity<TaskAcceptedResponse> updateMetadataValue(MetadataValueUpdateRequest request) {
		return ResponseEntity.accepted()
							 .body(metadataService.updateValueAsTask(request)
												  .toAcceptedResponse());
	}
}
