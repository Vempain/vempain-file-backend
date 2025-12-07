package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.ImageFileResponse;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.repository.files.ImageFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ImageFileService {

	private final ImageFileRepository imageFileRepository;

	@Transactional(readOnly = true)
	public PagedResponse<ImageFileResponse> findAll(int page, int size) {
		// Default: sort by file_path ascending
		return findAll(page, size, "filePath", Sort.Direction.ASC);
	}

	@Transactional(readOnly = true)
	public PagedResponse<ImageFileResponse> findAll(int page, int size, String sortBy, Sort.Direction direction) {
		var safePage = Math.max(0, page);
		var safeSize = Math.max(1, size);
		var sort     = Sort.by(direction == null ? Sort.Direction.ASC : direction, sortBy == null ? "filePath" : sortBy);
		var pageable = PageRequest.of(safePage, safeSize, sort);

		var pageResult = imageFileRepository.findAll(pageable);
		var content = pageResult.getContent()
								.stream()
								.map(ImageFileEntity::toResponse)
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
	public ImageFileResponse findById(long id) {
		var entityOpt = imageFileRepository.findById(id);
		return entityOpt.map(ImageFileEntity::toResponse)
						.orElse(null);
	}

	public HttpStatus delete(long id) {
		try {
			imageFileRepository.deleteById(id);
			return HttpStatus.OK;
		} catch (Exception e) {
			log.warn("Failed to delete archive file with id {}: {}", id, e.getMessage());
			return HttpStatus.NOT_FOUND;
		}
	}
}
