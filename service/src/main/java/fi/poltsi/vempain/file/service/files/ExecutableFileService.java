package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.ExecutableFileResponse;
import fi.poltsi.vempain.file.entity.ExecutableFileEntity;
import fi.poltsi.vempain.file.repository.files.ExecutableFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutableFileService {

	private final ExecutableFileRepository repository;

	@Transactional(readOnly = true)
	public PagedResponse<ExecutableFileResponse> findAll(PagedRequest pagedRequest) {
		var                                 safePage   = Math.max(0, pagedRequest.getPage());
		var                                 safeSize   = Math.min(Math.max(pagedRequest.getSize(), 1), 200);
		var                                 sort       = FileSearchHelper.buildSort(pagedRequest.getSortBy(), pagedRequest.getDirection());
		Specification<ExecutableFileEntity> spec       = FileSearchHelper.buildSpecification(pagedRequest.getSearch(), Boolean.TRUE.equals(pagedRequest.getCaseSensitive()));
		var                                 pageable   = PageRequest.of(safePage, safeSize, sort);
		var                                 pageResult = repository.findAll(spec, pageable);
		var content = pageResult.getContent()
		                        .stream()
		                        .map(ExecutableFileEntity::toResponse)
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
	public ExecutableFileResponse findById(long id) {
		return repository.findById(id)
						 .map(ExecutableFileEntity::toResponse)
						 .orElse(null);
	}

	public HttpStatus delete(long id) {
		if (!repository.existsById(id)) {
			log.warn("Executable file with id {} not found", id);
			return HttpStatus.NOT_FOUND;
		}
		repository.deleteById(id);
		return HttpStatus.OK;
	}
}

