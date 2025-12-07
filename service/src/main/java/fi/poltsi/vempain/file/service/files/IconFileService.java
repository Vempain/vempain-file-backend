package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.IconFileResponse;
import fi.poltsi.vempain.file.entity.IconFileEntity;
import fi.poltsi.vempain.file.repository.files.IconFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class IconFileService {

	private final IconFileRepository iconFileRepository;

	@Transactional(readOnly = true)
	public PagedResponse<IconFileResponse> findAll(int page, int size) {
		var pageable   = PageRequest.of(Math.max(0, page), Math.max(1, size));
		var pageResult = iconFileRepository.findAll(pageable);
		var content = pageResult.getContent()
								.stream()
								.map(IconFileEntity::toResponse)
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
	public IconFileResponse findById(long id) {
		var entityOpt = iconFileRepository.findById(id);
		return entityOpt.map(IconFileEntity::toResponse)
						.orElse(null);
	}

	public HttpStatus delete(long id) {
		try {
			iconFileRepository.deleteById(id);
			return HttpStatus.OK;
		} catch (Exception e) {
			log.warn("Failed to delete archive file with id {}: {}", id, e.getMessage());
			return HttpStatus.NOT_FOUND;
		}
	}
}
