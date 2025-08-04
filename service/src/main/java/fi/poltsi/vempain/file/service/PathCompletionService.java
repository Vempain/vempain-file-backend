package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.request.PathCompletionRequest;
import fi.poltsi.vempain.file.api.response.PathCompletionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import static fi.poltsi.vempain.file.api.PathCompletionEnum.ORIGINAL;

@Slf4j
@Service
@RequiredArgsConstructor
public class PathCompletionService {

	@Value("${vempain.original-root-directory}")
	private String originalRootDirectory;

	@Value("${vempain.export-root-directory}")
	private String exportedRootDirectory;

	public PathCompletionResponse completePath(PathCompletionRequest request) {
		// Normalize request path: expected to start with '/'
		var requestPath = request.getPath();
		if (requestPath == null || requestPath.isEmpty()) {
			requestPath = "/";
		}

		var completions = new ArrayList<String>();

		var rootDirectory = request.getType()
								   .equals(ORIGINAL) ? originalRootDirectory : exportedRootDirectory;

		try {
			// Determine the working directory by joining the configured root with the request path.
			var fullPath = Paths.get(rootDirectory, requestPath);

			if (Files.exists(fullPath) && Files.isDirectory(fullPath)) {
				// If an exact directory exists, list its immediate subdirectories.
				try (DirectoryStream<Path> stream = Files.newDirectoryStream(fullPath)) {
					for (var entry : stream) {
						if (Files.isDirectory(entry) && !entry.getFileName()
															  .toString()
															  .startsWith(".")) {
							// Construct completion as the relative path starting with a '/'
							var relative = "/" + fullPath.relativize(entry)
														 .toString()
														 .replace("\\", "/");
							// Prepend the current request path if not '/'
							if (!requestPath.equals("/")) {
								String prefix = requestPath.endsWith("/") ? requestPath.substring(0, requestPath.length() - 1) : requestPath;
								relative = prefix + relative;
							}
							completions.add(relative);
						}
					}
				}
			} else {
				// Else, perform prefix matching in the parent directory.
				var parentPath = fullPath.getParent();
				if (parentPath != null && Files.exists(parentPath)) {
					var prefix = fullPath.getFileName()
										 .toString();
					try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentPath)) {
						for (var entry : stream) {
							if (Files.isDirectory(entry) && entry.getFileName()
																 .toString()
																 .startsWith(prefix)) {
								String candidate = "/" + parentPath.relativize(entry)
																   .toString()
																   .replace("\\", "/");
								completions.add(candidate);
							}
						}
					}
				}
			}
		} catch (IOException e) {
			log.error("Received exception when scanning directories", e);
		}

		return new PathCompletionResponse(completions);
	}
}
