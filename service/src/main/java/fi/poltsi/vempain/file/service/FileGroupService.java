package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.request.FileGroupRequest;
import fi.poltsi.vempain.file.api.response.FileGroupListResponse;
import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FileGroupService {

	private final FileGroupRepository fileGroupRepository;
	private final FileRepository fileRepository;

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
															   .description(p.getDescription())
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

	@Transactional
	public FileGroupResponse addFileGroup(FileGroupRequest request) {
		var fileGroupEntity = new FileGroupEntity();
		fileGroupEntity.setPath(request.getPath());              // ensure path persisted
		fileGroupEntity.setGroupName(request.getGroupName());
		fileGroupEntity.setDescription(request.getDescription());
		if (request.getFileIds() != null) {
			List<FileEntity> files = fileRepository.findAllById(request.getFileIds());
			fileGroupEntity.replaceFiles(files);                 // mutate managed collection
		}
		FileGroupEntity saved = fileGroupRepository.save(fileGroupEntity);
		return saved.toResponse();
	}

	@Transactional
	public FileGroupResponse updateFileGroup(FileGroupRequest fileGroupRequest) {
		FileGroupEntity fileGroupEntity = fileGroupRepository.findById(fileGroupRequest.getId())
															 .orElseThrow(() -> new EntityNotFoundException("FileGroup not found: " + fileGroupRequest.getId()));
		fileGroupEntity.setPath(fileGroupRequest.getPath());     // allow path update if needed
		fileGroupEntity.setGroupName(fileGroupRequest.getGroupName());
		fileGroupEntity.setDescription(fileGroupRequest.getDescription());
		if (fileGroupRequest.getFileIds() != null) {
			List<FileEntity> files = fileRepository.findAllById(fileGroupRequest.getFileIds());
			fileGroupEntity.replaceFiles(files);                 // avoid setFiles (dereferencing)
		}
		FileGroupEntity saved = fileGroupRepository.save(fileGroupEntity);
		return saved.toResponse();
	}
}
