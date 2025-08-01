package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.api.request.TagRequest;
import fi.poltsi.vempain.file.api.response.TagResponse;
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
	public ResponseEntity<TagResponse> getTagById(Long id) {
		TagResponse tag = tagService.getTagById(id);
		return ResponseEntity.ok(tag);
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
}
