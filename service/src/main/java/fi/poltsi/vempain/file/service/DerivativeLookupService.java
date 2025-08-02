package fi.poltsi.vempain.file.service;

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
	@Value("${vempain.export-root-directory}")
	private String exportDirectory;

	public File findDerivative(String filename, String documentId) {
		String baseName = stripExtension(filename);
		try {
			return Files.walk(Path.of(exportDirectory))
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
			var xmpSection = MetadataTool.extractSection(file, "XMP-xmpMM");
			if (xmpSection == null) {
				return false;
			}
			String derived  = xmpSection.get("DerivedFromOriginalDocumentID");
			String original = xmpSection.get("OriginalDocumentID");
			return Objects.equals(documentId, derived) || Objects.equals(documentId, original);
		} catch (Exception e) {
			log.warn("Failed to extract metadata from file: {}", file.getAbsolutePath(), e);
			return false;
		}
	}

	private String stripExtension(String name) {
		int idx = name.lastIndexOf('.');
		return (idx > 0) ? name.substring(0, idx) : name;
	}
}
