package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.DataFileResponse;
import fi.poltsi.vempain.file.entity.DataFileEntity;
import fi.poltsi.vempain.file.repository.files.DataFileRepository;
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
public class DataFileService {

	private final DataFileRepository repository;

	@Transactional(readOnly = true)
	public PagedResponse<DataFileResponse> findAll(PagedRequest pagedRequest) {
		var                           safePage   = Math.max(0, pagedRequest.getPage());
		var                           safeSize   = Math.min(Math.max(pagedRequest.getSize(), 1), 200);
		var                           sort       = FileSearchHelper.buildSort(pagedRequest.getSortBy(), pagedRequest.getDirection());
		Specification<DataFileEntity> spec       = FileSearchHelper.buildSpecification(pagedRequest.getSearch(), Boolean.TRUE.equals(pagedRequest.getCaseSensitive()));
		var                           pageable   = PageRequest.of(safePage, safeSize, sort);
		var                           pageResult = repository.findAll(spec, pageable);
		var content = pageResult.getContent()
		                        .stream()
		                        .map(DataFileEntity::toResponse)
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
	public DataFileResponse findById(long id) {
		return repository.findById(id)
						 .map(DataFileEntity::toResponse)
						 .orElse(null);
	}

	public HttpStatus delete(long id) {
		if (!repository.existsById(id)) {
			log.warn("Data file with id {} not found", id);
			return HttpStatus.NOT_FOUND;
		}
		repository.deleteById(id);
		return HttpStatus.OK;
	}
}

