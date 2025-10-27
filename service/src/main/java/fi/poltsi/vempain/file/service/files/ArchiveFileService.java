package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.ArchiveFileResponse;
import fi.poltsi.vempain.file.entity.ArchiveFileEntity;
import fi.poltsi.vempain.file.repository.files.ArchiveFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ArchiveFileService {

	private final ArchiveFileRepository archiveFileRepository;

	@Transactional(readOnly = true)
	public PagedResponse<ArchiveFileResponse> findAll(int page, int size) {
		var pageable   = PageRequest.of(Math.max(0, page), Math.max(1, size));
		var pageResult = archiveFileRepository.findAll(pageable);
		var content = pageResult.getContent()
								.stream()
								.map(ArchiveFileEntity::toResponse)
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
	public ArchiveFileResponse findById(long id) {
		var entityOpt = archiveFileRepository.findById(id);
		return entityOpt.map(ArchiveFileEntity::toResponse)
						.orElse(null);
	}

	public HttpStatus delete(long id) {
		try {
			archiveFileRepository.deleteById(id);
			return HttpStatus.OK;
		} catch (Exception e) {
			log.warn("Failed to delete archive file with id {}: {}", id, e.getMessage());
			return HttpStatus.NOT_FOUND;
		}
	}
}
