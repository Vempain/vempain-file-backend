package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.rest.FileContentAPI;
import fi.poltsi.vempain.file.service.FileContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
public class FileContentController implements FileContentAPI {

	private final FileContentService fileContentService;

	@Override
	public ResponseEntity<Resource> getFileContent(long id) {
		var contentFile = fileContentService.resolveOriginalFile(id);
		var mediaType   = parseMediaType(contentFile.mimetype());
		var disposition = ContentDisposition.inline()
		                                    .filename(contentFile.filename(), StandardCharsets.UTF_8)
		                                    .build();
		var resource = new FileSystemResource(contentFile.absolutePath());

		return ResponseEntity.ok()
		                     .contentType(mediaType)
		                     .contentLength(contentFile.size())
		                     .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
		                     .body(resource);
	}

	private MediaType parseMediaType(String mimetype) {
		if (mimetype == null || mimetype.isBlank()) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
		try {
			return MediaType.parseMediaType(mimetype);
		} catch (Exception ignored) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}
}

