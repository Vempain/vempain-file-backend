package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.InteractiveFileResponse;
import fi.poltsi.vempain.file.entity.InteractiveFileEntity;
import fi.poltsi.vempain.file.repository.files.InteractiveFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InteractiveFileService {

	private final InteractiveFileRepository repository;

	@Transactional(readOnly = true)
	public PagedResponse<InteractiveFileResponse> findAll(int page, int size) {
		var pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by("filename")
																				.ascending());
		var p        = repository.findAll(pageable);
		var content  = p.getContent()
						.stream()
						.map(InteractiveFileEntity::toResponse)
						.toList();
		return PagedResponse.of(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), p.isFirst(), p.isLast());
	}

	@Transactional(readOnly = true)
	public InteractiveFileResponse findById(long id) {
		return repository.findById(id)
						 .map(InteractiveFileEntity::toResponse)
						 .orElse(null);
	}

	public HttpStatus delete(long id) {
		try {
			repository.deleteById(id);
			return HttpStatus.OK;
		} catch (Exception e) {
			log.warn("Failed to delete interactive file {}: {}", id, e.getMessage());
			return HttpStatus.NOT_FOUND;
		}
	}
}

