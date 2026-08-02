package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.repository.files.ThumbFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FileResponseEnricher {

	private final ThumbFileRepository thumbFileRepository;

	@SuppressWarnings("unchecked")
	public <T extends FileResponse> T toResponse(FileEntity entity) {
		return (T) enrich(entity.toResponse());
	}

	@SuppressWarnings("unchecked")
	public <T extends FileResponse> List<T> toResponses(List<? extends FileEntity> entities) {
		var responses = entities.stream()
		                        .map(FileEntity::toResponse)
		                        .toList();
		enrichAll(responses);
		return (List<T>) responses;
	}

	public FileResponse enrich(FileResponse response) {
		if (response.getId() != null) {
			response.setThumbnailId(thumbFileRepository.findThumbnailIdByTargetFileId(response.getId())
			                                           .orElse(null));
		}
		return response;
	}

	public void enrichAll(List<? extends FileResponse> responses) {
		if (responses == null || responses.isEmpty()) {
			return;
		}
		var targetIds = responses.stream()
		                         .map(FileResponse::getId)
		                         .filter(id -> id != null)
		                         .distinct()
		                         .toList();
		if (targetIds.isEmpty()) {
			return;
		}
		Map<Long, Long> thumbnailIds = new HashMap<>();
		for (Object[] row : thumbFileRepository.findThumbnailIdsByTargetFileIds(targetIds)) {
			thumbnailIds.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
		}
		responses.forEach(response -> response.setThumbnailId(thumbnailIds.get(response.getId())));
	}
}
