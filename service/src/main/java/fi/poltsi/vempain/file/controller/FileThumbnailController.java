package fi.poltsi.vempain.file.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import fi.poltsi.vempain.file.api.response.files.FileThumbnailResponse;
import fi.poltsi.vempain.file.rest.FileThumbnailAPI;
import fi.poltsi.vempain.file.service.ThumbnailService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FileThumbnailController implements FileThumbnailAPI {

    private final ThumbnailService thumbnailService;

    @Override
    public ResponseEntity<Resource> getThumbnail(long id) {
        var contentFile = thumbnailService.resolveContent(id);
        var disposition = ContentDisposition.inline()
            .filename(contentFile.filename(), StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .contentType(parseMediaType(contentFile.mimetype()))
            .contentLength(contentFile.size())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
            .body(new FileSystemResource(contentFile.absolutePath()));
    }

    @Override
    public ResponseEntity<FileThumbnailResponse> getThumbnailMetadata(long id) {
        return thumbnailService.findByFileId(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound()
                .build());
    }

    @Override
    public ResponseEntity<Void> deleteThumbnail(long id) {
        return ResponseEntity.status(thumbnailService.delete(id))
            .build();
    }

    private MediaType parseMediaType(String mimetype) {
        if (mimetype == null
            || mimetype.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimetype);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
