package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.VideoFileResponse;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.files.VideoFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoFileService {

	private final VideoFileRepository videoFileRepository;

	@Transactional(readOnly = true)
	public PagedResponse<VideoFileResponse> findAll(PagedRequest pagedRequest) {
		var                            safePage   = Math.max(0, pagedRequest.getPage());
		var                            safeSize   = Math.min(Math.max(pagedRequest.getSize(), 1), 200);
		var                            sort       = FileSearchHelper.buildSort(pagedRequest.getSortBy(), pagedRequest.getDirection());
		Specification<VideoFileEntity> spec       = FileSearchHelper.buildSpecification(pagedRequest.getSearch(), Boolean.TRUE.equals(pagedRequest.getCaseSensitive()));
		var                            pageable   = PageRequest.of(safePage, safeSize, sort);
		var                            pageResult = videoFileRepository.findAll(spec, pageable);
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
		if (!videoFileRepository.existsById(id)) {
			log.warn("Video file with id {} not found", id);
			return HttpStatus.NOT_FOUND;
		}
		videoFileRepository.deleteById(id);
		return HttpStatus.OK;
	}
}
