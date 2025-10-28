package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.FileGroupListResponse;
import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FileGroupService {

	private final FileGroupRepository fileGroupRepository;

	@Transactional(readOnly = true)
	public PagedResponse<FileGroupListResponse> getAll(int page, int size) {
		// Default: sort by path ascending
		return getAll(page, size, "path", Sort.Direction.ASC);
	}

	@Transactional(readOnly = true)
	public PagedResponse<FileGroupListResponse> getAll(int page, int size, String sortBy, Sort.Direction direction) {
		var safePage = Math.max(0, page);
		var safeSize = Math.max(1, size);
		var sort     = Sort.by(direction == null ? Sort.Direction.ASC : direction, sortBy == null ? "path" : sortBy);
		var pageable = PageRequest.of(safePage, safeSize, sort);

		// Use projection to avoid loading FileEntity list; fetch only counts
		var pageResult = fileGroupRepository.findAllWithFileCounts(pageable);
		var content = pageResult.getContent()
								.stream()
								.map(p -> FileGroupListResponse.builder()
															   .id(p.getId())
															   .path(p.getPath())
															   .groupName(p.getGroupName())
															   .fileCount(p.getFileCount())
															   .build())
								.toList();

		return PagedResponse.of(
				content,
				pageResult.getNumber(),
				pageResult.getSize(),
				pageResult.getTotalElements(),
				pageResult.getTotalPages(),
				pageResult.isFirst(),
				pageResult.isLast()
		);
	}

	@Transactional(readOnly = true)
	public FileGroupResponse getById(Long id) {
		FileGroupEntity entity = fileGroupRepository.findById(id)
													.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File group not found"));
		return entity.toResponse();
	}
}
