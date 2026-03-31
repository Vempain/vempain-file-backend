package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.VectorFileResponse;
import fi.poltsi.vempain.file.entity.VectorFileEntity;
import fi.poltsi.vempain.file.repository.files.VectorFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class VectorFileService {

	private final VectorFileRepository vectorFileRepository;

	@Transactional(readOnly = true)
	public PagedResponse<VectorFileResponse> findAll(PagedRequest pagedRequest) {
		var                             safePage   = Math.max(0, pagedRequest.getPage());
		var                             safeSize   = Math.min(Math.max(pagedRequest.getSize(), 1), 200);
		var                             sort       = FileSearchHelper.buildSort(pagedRequest.getSortBy(), pagedRequest.getDirection());
		Specification<VectorFileEntity> spec       = FileSearchHelper.buildSpecification(pagedRequest.getSearch(), Boolean.TRUE.equals(pagedRequest.getCaseSensitive()));
		var                             pageable   = PageRequest.of(safePage, safeSize, sort);
		var                             pageResult = vectorFileRepository.findAll(spec, pageable);
		var content = pageResult.getContent()
								.stream()
								.map(VectorFileEntity::toResponse)
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
	public VectorFileResponse findById(long id) {
		var entityOpt = vectorFileRepository.findById(id);
		return entityOpt.map(VectorFileEntity::toResponse)
						.orElse(null);
	}

	public HttpStatus delete(long id) {
		if (!vectorFileRepository.existsById(id)) {
			log.warn("Vector file with id {} not found", id);
			return HttpStatus.NOT_FOUND;
		}
		vectorFileRepository.deleteById(id);
		return HttpStatus.OK;
	}
}
