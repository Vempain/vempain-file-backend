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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FileGroupService {

	private final FileGroupRepository fileGroupRepository;
	private final FileRepository fileRepository;

	@Transactional(readOnly = true)
	public PagedResponse<FileGroupListResponse> getAll(int page, int size) {
		return getAll(page, size, "path", "ASC", null, false);
	}

	@Transactional(readOnly = true)
	public PagedResponse<FileGroupListResponse> getAll(int page,
													   int size,
													   String sort,
													   String direction,
													   String search,
													   boolean caseSensitive) {
		var safePage = Math.max(0, page);
		var safeSize = Math.min(Math.max(size, 1), 200);
		var sortSpec = buildSort(sort, direction);
		var pageable = PageRequest.of(safePage, safeSize, sortSpec);

		var pageResult = fileGroupRepository.searchFileGroups(search, caseSensitive, pageable);
		var content = pageResult.getContent()
								.stream()
								.map(row -> FileGroupListResponse.builder()
																 .id(row.id())
																 .path(row.path())
																 .groupName(row.groupName())
																 .description(row.description())
																 .fileCount(row.fileCount())
																 .build())
								.toList();

		return PagedResponse.of(content,
								pageResult.getNumber(),
								pageResult.getSize(),
								pageResult.getTotalElements(),
								pageResult.getTotalPages(),
								pageResult.isFirst(),
								pageResult.isLast());
	}

	private Sort buildSort(String sort, String direction) {
		String property = switch (sort == null ? "path" : sort.toLowerCase()) {
			case "id" -> "id";
			case "groupname", "group_name" -> "groupName";
			case "description" -> "description";
			default -> "path";
		};
		Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
		return Sort.by(dir, property);
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
			fileGroupEntity.replaceFiles(files);                 // mutate managed collection + inverse
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
			List<FileEntity> newFiles = fileRepository.findAllById(fileGroupRequest.getFileIds());

			// Enforce: a file must always belong to at least one group
			Set<FileEntity> current = new HashSet<>(fileGroupEntity.getFiles());
			Set<FileEntity> newSet  = new HashSet<>(newFiles);
			current.removeAll(newSet); // these would be removed from this group
			for (FileEntity f : current) {
				if (f.getFileGroups() == null || f.getFileGroups()
												  .size() <= 1) {
					throw new ResponseStatusException(
							HttpStatus.BAD_REQUEST,
							"Cannot remove file " + f.getId() + " from its last group"
					);
				}
			}

			fileGroupEntity.replaceFiles(newFiles);                 // mutate managed collection + inverse
		}
		FileGroupEntity saved = fileGroupRepository.save(fileGroupEntity);
		return saved.toResponse();
	}
}
