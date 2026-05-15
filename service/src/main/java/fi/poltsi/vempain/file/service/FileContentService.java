package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.repository.files.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileContentService {

	private final FileRepository fileRepository;

	@Value("${vempain.original-root-directory}")
	private String originalRootDirectory;

	public ContentFile resolveOriginalFile(long fileId) {
		var entity = fileRepository.findById(fileId)
		                           .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
		                                                                          "File with id %d not found".formatted(fileId)));

		var rootPath = Paths.get(originalRootDirectory)
		                    .normalize()
		                    .toAbsolutePath();

		var relativeDir = normalizeRelativeDirectory(entity.getFilePath());
		var resolvedPath = rootPath.resolve(relativeDir)
		                           .resolve(entity.getFilename())
		                           .normalize();

		if (!resolvedPath.startsWith(rootPath)) {
			log.warn("Rejected file content request outside original root. fileId={}, path={}", fileId, resolvedPath);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolved file path is outside configured original root");
		}

		if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
			                                  "File content for id %d not found from storage".formatted(fileId));
		}

		try {
			return new ContentFile(
					resolvedPath,
					entity.getFilename(),
					entity.getMimetype(),
					Files.size(resolvedPath)
			);
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
			                                  "Failed to resolve file size for id %d".formatted(fileId), e);
		}
	}

	private String normalizeRelativeDirectory(String filePath) {
		if (filePath == null || filePath.isBlank() || "/".equals(filePath.trim())) {
			return "";
		}

		var normalized = filePath.trim()
		                         .replace('\\', '/');
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		return normalized;
	}

	public record ContentFile(Path absolutePath, String filename, String mimetype, long size) {
	}
}

