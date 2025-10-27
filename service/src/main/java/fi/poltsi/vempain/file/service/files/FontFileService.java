package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.FontFileResponse;
import fi.poltsi.vempain.file.entity.FontFileEntity;
import fi.poltsi.vempain.file.repository.files.FontFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class FontFileService {

	private final FontFileRepository fontFileRepository;

	@Transactional(readOnly = true)
	public PagedResponse<FontFileResponse> findAll(int page, int size) {
		var pageable   = PageRequest.of(Math.max(0, page), Math.max(1, size));
		var pageResult = fontFileRepository.findAll(pageable);
		var content = pageResult.getContent()
								.stream()
								.map(FontFileEntity::toResponse)
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
	public FontFileResponse findById(long id) {
		var entityOpt = fontFileRepository.findById(id);
		return entityOpt.map(FontFileEntity::toResponse)
						.orElse(null);
	}

	public HttpStatus delete(long id) {
		try {
			fontFileRepository.deleteById(id);
			return HttpStatus.OK;
		} catch (Exception e) {
			log.warn("Failed to delete archive file with id {}: {}", id, e.getMessage());
			return HttpStatus.NOT_FOUND;
		}
	}
}
