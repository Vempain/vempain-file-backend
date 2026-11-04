package fi.poltsi.vempain.file.service;

import java.util.List;

import org.springframework.stereotype.Service;

import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.repository.FileThumbnailRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileResponseEnricher {

    private final FileThumbnailRepository thumbnailRepository;

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
            response.setThumbnail(thumbnailRepository.findByFileId(response.getId())
                .map(thumbnail -> thumbnail.toResponse())
                .orElse(null));
        }
        return response;
    }

    public void enrichAll(List<? extends FileResponse> responses) {
        if (responses == null
            || responses.isEmpty()) {
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
        var thumbnailsByFileId = thumbnailRepository.findByFileIdIn(targetIds)
            .stream()
            .collect(java.util.stream.Collectors.toMap(thumbnail -> thumbnail.getFile()
                .getId(), thumbnail -> thumbnail));
        responses.forEach(response -> response.setThumbnail(java.util.Optional.ofNullable(thumbnailsByFileId.get(response.getId()))
            .map(thumbnail -> thumbnail.toResponse())
            .orElse(null)));
    }
}
