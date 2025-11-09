package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.DataFileResponse;
import fi.poltsi.vempain.file.entity.DataFileEntity;
import fi.poltsi.vempain.file.repository.files.DataFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataFileService {

	private final DataFileRepository repository;

	@Transactional(readOnly = true)
	public PagedResponse<DataFileResponse> findAll(int page, int size) {
		var p = repository.findAll(PageRequest.of(Math.max(0, page), Math.max(1, size)));
		var c = p.getContent()
				 .stream()
				 .map(DataFileEntity::toResponse)
				 .toList();
		return PagedResponse.of(c, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(), p.isFirst(), p.isLast());
	}

	@Transactional(readOnly = true)
	public DataFileResponse findById(long id) {
		return repository.findById(id)
						 .map(DataFileEntity::toResponse)
						 .orElse(null);
	}

	public HttpStatus delete(long id) {
		try {
			repository.deleteById(id);
			return HttpStatus.OK;
		} catch (Exception e) {
			log.warn("Failed to delete data file {}: {}", id, e.getMessage());
			return HttpStatus.NOT_FOUND;
		}
	}
}

