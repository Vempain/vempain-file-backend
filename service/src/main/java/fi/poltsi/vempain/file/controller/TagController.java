package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.request.TagOperationRequest;
import fi.poltsi.vempain.file.api.request.TagRequest;
import fi.poltsi.vempain.file.api.response.TagResponse;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.rest.TagAPI;
import fi.poltsi.vempain.file.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TagController implements TagAPI {

	private final TagService tagService;

	@Override
	public ResponseEntity<List<TagResponse>> getAllTags() {
		List<TagResponse> tags = tagService.getAllTags();
		return ResponseEntity.ok(tags);
	}

	@Override
	public ResponseEntity<PagedResponse<TagResponse>> getAllTagsPageable(PagedRequest pagedRequest) {
		return ResponseEntity.ok(tagService.getAllTagsPageable(pagedRequest));
	}

	@Override
	public ResponseEntity<TagResponse> getTagById(Long id) {
		TagResponse tag = tagService.getTagById(id);
		return ResponseEntity.ok(tag);
	}

	@Override
	public ResponseEntity<PagedResponse<FileResponse>> getFilesByTag(Long id, PagedRequest pagedRequest) {
		return ResponseEntity.ok(tagService.getFilesByTag(id, pagedRequest));
	}

	@Override
	public ResponseEntity<TagResponse> createTag(TagRequest tagRequest) {
		TagResponse tag = tagService.createTag(tagRequest);
		return ResponseEntity.ok(tag);
	}

	@Override
	public ResponseEntity<TagResponse> updateTag(TagRequest tagRequest) {
		TagResponse tag = tagService.updateTag(tagRequest);
		return ResponseEntity.ok(tag);
	}

	@Override
	public ResponseEntity<Void> deleteTag(Long id) {
		tagService.deleteTag(id);
		return ResponseEntity.noContent()
		                     .build();
	}

	@Override
	public ResponseEntity<Void> addTag(TagOperationRequest request) {
		tagService.addTag(request);
		return ResponseEntity.noContent()
		                     .build();
	}

	@Override
	public ResponseEntity<Void> removeTag(TagOperationRequest request) {
		tagService.removeTag(request, false);
		return ResponseEntity.noContent()
		                     .build();
	}

	@Override
	public ResponseEntity<Void> replaceTag(TagOperationRequest request) {
		tagService.replaceTag(request, false);
		return ResponseEntity.noContent()
		                     .build();
	}

	@Override
	public ResponseEntity<Void> renameTag(TagOperationRequest request) {
		tagService.renameTag(request, false);
		return ResponseEntity.noContent()
		                     .build();
	}

	@Override
	public ResponseEntity<Void> removeTagFromAll(TagOperationRequest request) {
		tagService.removeTag(request, true);
		return ResponseEntity.noContent()
		                     .build();
	}

	@Override
	public ResponseEntity<Void> replaceTagAcrossAll(TagOperationRequest request) {
		tagService.replaceTag(request, true);
		return ResponseEntity.noContent()
		                     .build();
	}

	@Override
	public ResponseEntity<Void> renameTagAcrossAll(TagOperationRequest request) {
		tagService.renameTag(request, true);
		return ResponseEntity.noContent()
		                     .build();
	}
}
