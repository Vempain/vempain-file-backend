package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.tools.MetadataTool;
import fi.poltsi.vempain.file.tools.SampleFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests (UTC) for {@link DerivativeLookupService}.
 */
@ExtendWith(MockitoExtension.class)
class DerivativeLookupServiceUTC {

	@TempDir
	Path tempExportDir;
	@Mock
	private ExportFileRepository    exportFileRepository;
	@Mock
	private FileRepository          fileRepository;
	@InjectMocks
	private DerivativeLookupService derivativeLookupService;

	@BeforeEach
	void setup() {
		ReflectionTestUtils.setField(derivativeLookupService, "exportDirectory", tempExportDir.toString());
	}

	// ------------------------------------------------------------------
	// findOriginal
	// ------------------------------------------------------------------
	@Nested
	@DisplayName("findOriginal")
	class FindOriginal {

		@Test
		void foundInDb_returnsFile() {
			var mockFile = new fi.poltsi.vempain.file.entity.ImageFileEntity();
			mockFile.setId(1L);
			mockFile.setFilename("original.jpg");
			mockFile.setFilePath("/originals");
			when(fileRepository.findByOriginalDocumentId("doc-123")).thenReturn(mockFile);

			var result = derivativeLookupService.findOriginal("/sub", "file.jpg", "doc-123");
			assertThat(result).isNotNull();
		}

		@Test
		void notFoundInDb_noFilesInDir_returnsNull() throws IOException {
			when(fileRepository.findByOriginalDocumentId("doc-missing")).thenReturn(null);

			var sub = tempExportDir.resolve("sub");
			Files.createDirectories(sub);

			var result = derivativeLookupService.findOriginal("/sub", "file.jpg", "doc-missing");
			assertNull(result);
		}

		@Test
		void nonExistentDirectory_returnsNull() {
			when(fileRepository.findByOriginalDocumentId("doc-x")).thenReturn(null);

			var result = derivativeLookupService.findOriginal("/nonexistent", "file.jpg", "doc-x");
			assertNull(result);
		}
	}

	// ------------------------------------------------------------------
	// findDerivative
	// ------------------------------------------------------------------
	@Nested
	@DisplayName("findDerivative")
	class FindDerivative {

		@Test
		void foundInDb_returnsFile() {
			var mockExport = ExportFileEntity.builder()
			                                 .id(1L)
			                                 .filePath("/exports/derivative.jpg")
			                                 .build();
			when(exportFileRepository.findByOriginalDocumentId("doc-456")).thenReturn(mockExport);

			var result = derivativeLookupService.findDerivative("/sub", "file.jpg", "doc-456");
			assertThat(result).isNotNull();
		}

		@Test
		void notFoundInDb_noFilesInDir_returnsNull() throws IOException {
			when(exportFileRepository.findByOriginalDocumentId("doc-notfound")).thenReturn(null);

			var sub = tempExportDir.resolve("sub2");
			Files.createDirectories(sub);

			var result = derivativeLookupService.findDerivative("/sub2", "file.jpg", "doc-notfound");
			assertNull(result);
		}

		@Test
		void nonExistentDirectory_returnsNull() {
			when(exportFileRepository.findByOriginalDocumentId("doc-xyz")).thenReturn(null);

			var result = derivativeLookupService.findDerivative("/nonexistent2", "file.jpg", "doc-xyz");
			assertNull(result);
		}
	}


	@Nested
	@DisplayName("filesystem fallback")
	class FilesystemFallback {

		@Test
		void findOriginalFallsBackToAFileCarryingTheDocumentId() throws Exception {
			when(fileRepository.findByOriginalDocumentId("doc-fs")).thenReturn(null);
			var sub   = Files.createDirectories(tempExportDir.resolve("fs"));
			var match = SampleFiles.jpeg(sub.resolve("photo.jpg"));
			MetadataTool.writeMetadataValue(match, "XMP-xmpMM", "OriginalDocumentID", "doc-fs");
			SampleFiles.jpeg(sub.resolve("other.jpg"));

			assertThat(derivativeLookupService.findOriginal("/fs", "photo.png", "doc-fs")).isEqualTo(match);
		}

		@Test
		void findDerivativeFallsBackToAFileDerivedFromTheDocumentAndIgnoresFilesWithoutXmp() throws Exception {
			when(exportFileRepository.findByOriginalDocumentId("doc-d")).thenReturn(null);
			var sub = Files.createDirectories(tempExportDir.resolve("d"));
			Files.writeString(sub.resolve("photo.txt"), "no xmp at all");
			assertThat(derivativeLookupService.findDerivative("/d", "photo.jpg", "doc-d")).isNull();

			var unrelated = SampleFiles.jpeg(sub.resolve("photo.jpeg"));
			MetadataTool.writeMetadataValue(unrelated, "XMP-xmpMM", "OriginalDocumentID", "doc-other");
			assertThat(derivativeLookupService.findDerivative("/d", "photo.jpg", "doc-d")).isNull();

			var match = SampleFiles.jpeg(sub.resolve("photo.jpg"));
			MetadataTool.writeMetadataValue(match, "XMP-xmpMM", "DerivedFromOriginalDocumentID", "doc-d");
			assertThat(derivativeLookupService.findDerivative("/d", "photo.jpg", "doc-d")).isEqualTo(match);

			// a file whose metadata cannot be read is logged and skipped
			try (var tool = org.mockito.Mockito.mockStatic(MetadataTool.class)) {
				tool.when(() -> MetadataTool.extractMetadataJson(org.mockito.ArgumentMatchers.any()))
					.thenThrow(new IOException("exiftool missing"));
				assertThat(derivativeLookupService.findDerivative("/d", "photo.jpg", "doc-d")).isNull();
			}
		}
	}
}
