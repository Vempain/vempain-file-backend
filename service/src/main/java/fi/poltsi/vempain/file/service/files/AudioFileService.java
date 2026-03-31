package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.AudioFileResponse;
import fi.poltsi.vempain.file.entity.AudioFileEntity;
import fi.poltsi.vempain.file.repository.files.AudioFileRepository;
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
public class AudioFileService {

	private final AudioFileRepository audioFileRepository;

	@Transactional(readOnly = true)
	public PagedResponse<AudioFileResponse> findAll(PagedRequest pagedRequest) {
		var                            safePage   = Math.max(0, pagedRequest.getPage());
		var                            safeSize   = Math.min(Math.max(pagedRequest.getSize(), 1), 200);
		var                            sort       = FileSearchHelper.buildSort(pagedRequest.getSortBy(), pagedRequest.getDirection());
		Specification<AudioFileEntity> spec       = FileSearchHelper.buildSpecification(pagedRequest.getSearch(), Boolean.TRUE.equals(pagedRequest.getCaseSensitive()));
		var                            pageable   = PageRequest.of(safePage, safeSize, sort);
		var                            pageResult = audioFileRepository.findAll(spec, pageable);
		var content = pageResult.getContent()
								.stream()
								.map(AudioFileEntity::toResponse)
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
	public AudioFileResponse findById(long id) {
		var entityOpt = audioFileRepository.findById(id);
		return entityOpt.map(AudioFileEntity::toResponse)
						.orElse(null);
	}

	public HttpStatus delete(long id) {
		if (!audioFileRepository.existsById(id)) {
			log.warn("Audio file with id {} not found", id);
			return HttpStatus.NOT_FOUND;
		}
		audioFileRepository.deleteById(id);
		return HttpStatus.OK;
	}
}
