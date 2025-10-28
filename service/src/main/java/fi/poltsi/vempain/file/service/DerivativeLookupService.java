// File: `service/src/main/java/fi/poltsi/vempain/file/service/DerivativeLookupService.java`
package fi.poltsi.vempain.file.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.tools.MetadataTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@RequiredArgsConstructor
@Slf4j
@Service
public class DerivativeLookupService {
	private final ExportFileRepository exportFileRepository;
	private final FileRepository       fileRepository;
	@Value("${vempain.export-root-directory}")
	private String exportDirectory;

	public File findOriginal(String subPath, String filename, String documentId) {
		var baseName = stripExtension(filename);

		// First, check the database for a matching exported file
		var match = fileRepository.findByOriginalDocumentId(documentId);

		if (match != null) {
			var originalFile = exportDirectory + File.pathSeparator + match.getFilePath() + File.separator + match.getFilename();
			return new File(originalFile);
		}

		// Fallback to filesystem scan if not found in DB
		var searchDir = Path.of(exportDirectory, subPath);

		try {
			return Files.walk(searchDir)
						.filter(Files::isRegularFile)
						.filter(path -> stripExtension(path.getFileName()
														   .toString()).equals(baseName))
						.map(Path::toFile)
						.filter(file -> hasMatchingDocumentId(file, documentId))
						.findFirst()
						.orElse(null);
		} catch (IOException e) {
			log.error("Error searching for original file", e);
			return null;
		}
	}

	public File findDerivative(String subPath, String filename, String documentId) {
		String baseName = stripExtension(filename);

		// First, check the database for a matching exported file
		ExportFileEntity match = exportFileRepository.findByOriginalDocumentId(documentId);

		if (match != null) {
			return new File(exportDirectory, match.getFilePath());
		}

		// Fallback to filesystem scan if not found in DB
		var searchDir = Path.of(exportDirectory, subPath);

		try {
			return Files.walk(searchDir)
						.filter(Files::isRegularFile)
						.filter(path -> stripExtension(path.getFileName()
														   .toString()).equals(baseName))
						.map(Path::toFile)
						.filter(file -> hasMatchingDocumentId(file, documentId))
						.findFirst()
						.orElse(null);
		} catch (IOException e) {
			log.error("Error searching for derivative file", e);
			return null;
		}
	}

	private boolean hasMatchingDocumentId(File file, String documentId) {
		try {
			var metaJson = MetadataTool.extractMetadataJson(file);
			var mapper   = new ObjectMapper();
			var root     = mapper.readTree(metaJson);
			if (root.isArray() && root.size() > 0) {
				var xmpSection = root.get(0)
									 .get("XMP-xmpMM");
				if (xmpSection == null) {
					return false;
				}
				var derived = xmpSection.has("DerivedFromOriginalDocumentID")
							  ? xmpSection.get("DerivedFromOriginalDocumentID")
										  .asText() : null;
				var original = xmpSection.has("OriginalDocumentID")
							   ? xmpSection.get("OriginalDocumentID")
										   .asText() : null;
				return Objects.equals(documentId, derived) || Objects.equals(documentId, original);
			}
		} catch (Exception e) {
			log.warn("Failed to extract metadata from file: {}", file.getAbsolutePath(), e);
		}
		return false;
	}

	private String stripExtension(String name) {
		int idx = name.lastIndexOf('.');
		return (idx > 0) ? name.substring(0, idx) : name;
	}
}
