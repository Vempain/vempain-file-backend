package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.DocumentFileResponse;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.repository.DocumentFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class DocumentFileService {

	private final DocumentFileRepository documentFileRepository;

	@Transactional(readOnly = true)
	public PagedResponse<DocumentFileResponse> findAll(int page, int size) {
		var pageable   = PageRequest.of(Math.max(0, page), Math.max(1, size));
		var pageResult = documentFileRepository.findAll(pageable);
		var content = pageResult.getContent()
								.stream()
								.map(DocumentFileEntity::toResponse)
								.toList();
		var response = PagedResponse.of(
				content,
				pageResult.getNumber(),
				pageResult.getSize(),
				pageResult.getTotalElements(),
				pageResult.getTotalPages(),
				pageResult.isFirst(),
				pageResult.isLast()
		);
		return response;

	}

	@Transactional(readOnly = true)
	public DocumentFileResponse findById(long id) {
		var entityOpt = documentFileRepository.findById(id);
		return entityOpt.map(DocumentFileEntity::toResponse)
						.orElse(null);
	}

	public HttpStatus delete(long id) {
		try {
			documentFileRepository.deleteById(id);
			return HttpStatus.OK;
		} catch (Exception e) {
			log.warn("Failed to delete archive file with id {}: {}", id, e.getMessage());
			return HttpStatus.NOT_FOUND;
		}
	}
}
