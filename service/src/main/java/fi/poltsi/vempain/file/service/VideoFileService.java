package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.VideoFileResponse;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.VideoFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoFileService {

	private final VideoFileRepository videoFileRepository;

	@Transactional(readOnly = true)
	public PagedResponse<VideoFileResponse> findAll(int page, int size) {
		var pageable   = PageRequest.of(Math.max(0, page), Math.max(1, size));
		var pageResult = videoFileRepository.findAll(pageable);
		var content = pageResult.getContent()
								.stream()
								.map(VideoFileEntity::toResponse)
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
	public VideoFileResponse findById(long id) {
		var entityOpt = videoFileRepository.findById(id);
		return entityOpt.map(VideoFileEntity::toResponse)
						.orElse(null);

	}

	public HttpStatus delete(long id) {
		try {
			videoFileRepository.deleteById(id);
			return HttpStatus.OK;
		} catch (Exception e) {
			log.warn("Failed to delete archive file with id {}: {}", id, e.getMessage());
			return HttpStatus.NOT_FOUND;
		}
	}
}
