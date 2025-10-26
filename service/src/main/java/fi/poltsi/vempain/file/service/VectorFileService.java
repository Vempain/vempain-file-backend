package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.VectorFileResponse;
import fi.poltsi.vempain.file.entity.VectorFileEntity;
import fi.poltsi.vempain.file.repository.VectorFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class VectorFileService {

	private final VectorFileRepository vectorFileRepository;

	@Transactional(readOnly = true)
	public PagedResponse<VectorFileResponse> findAll(int page, int size) {
		var pageable   = PageRequest.of(Math.max(0, page), Math.max(1, size));
		var pageResult = vectorFileRepository.findAll(pageable);
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
		try {
			vectorFileRepository.deleteById(id);
			return HttpStatus.OK;
		} catch (Exception e) {
			log.warn("Failed to delete archive file with id {}: {}", id, e.getMessage());
			return HttpStatus.NOT_FOUND;
		}
	}
}
