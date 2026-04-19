package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.response.files.MusicFileResponse;
import fi.poltsi.vempain.file.entity.MusicFileEntity;
import fi.poltsi.vempain.file.repository.files.MusicFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class MusicFileService {

	private final MusicFileRepository musicFileRepository;

	@Transactional(readOnly = true)
	public PagedResponse<MusicFileResponse> findAll(PagedRequest pagedRequest) {
		var                           safePage   = Math.max(0, pagedRequest.getPage());
		var                           safeSize   = Math.min(Math.max(pagedRequest.getSize(), 1), 200);
		var                           sort       = FileSearchHelper.buildSort(pagedRequest.getSortBy(), pagedRequest.getDirection());
		Specification<MusicFileEntity> spec      = FileSearchHelper.buildSpecification(pagedRequest.getSearch(), Boolean.TRUE.equals(pagedRequest.getCaseSensitive()));
		var                           pageable   = PageRequest.of(safePage, safeSize, sort);
		var                           pageResult = musicFileRepository.findAll(spec, pageable);
		var content = pageResult.getContent()
								.stream()
								.map(MusicFileEntity::toResponse)
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
	public MusicFileResponse findById(long id) {
		var entityOpt = musicFileRepository.findById(id);
		return entityOpt.map(MusicFileEntity::toResponse)
						.orElse(null);
	}

	public HttpStatus delete(long id) {
		if (!musicFileRepository.existsById(id)) {
			log.warn("Music file with id {} not found", id);
			return HttpStatus.NOT_FOUND;
		}
		musicFileRepository.deleteById(id);
		return HttpStatus.OK;
	}

	/**
	 * Returns all music files ordered by artist, album, and track number — suitable
	 * for CSV generation.
	 */
	@Transactional(readOnly = true)
	public List<MusicFileEntity> findAllOrdered() {
		return musicFileRepository.findAllByOrderByArtistAscAlbumAscTrackNumberAsc();
	}
}
