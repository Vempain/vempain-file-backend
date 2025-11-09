package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.BinaryFileResponse;
import fi.poltsi.vempain.file.entity.BinaryFileEntity;
import fi.poltsi.vempain.file.repository.files.BinaryFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinaryFileService {

	private final BinaryFileRepository repository;

	@Transactional(readOnly = true)
	public PagedResponse<BinaryFileResponse> findAll(int page, int size) {
		var p       = repository.findAll(PageRequest.of(Math.max(0, page), Math.max(1, size)));
		var content = p.getContent()
					   .stream()
					   .map(BinaryFileEntity::toResponse)
					   .toList();
		return PagedResponse.of(content, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), p.isFirst(), p.isLast());
	}

	@Transactional(readOnly = true)
	public BinaryFileResponse findById(long id) {
		return repository.findById(id)
						 .map(BinaryFileEntity::toResponse)
						 .orElse(null);
	}

	public HttpStatus delete(long id) {
		try {
			repository.deleteById(id);
			return HttpStatus.OK;
		} catch (Exception e) {
			log.warn("Failed to delete binary file {}: {}", id, e.getMessage());
			return HttpStatus.NOT_FOUND;
		}
	}
}

