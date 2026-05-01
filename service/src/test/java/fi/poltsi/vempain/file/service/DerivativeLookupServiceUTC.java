package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
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

    @Mock
    private ExportFileRepository exportFileRepository;
    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private DerivativeLookupService derivativeLookupService;

    @TempDir
    Path tempExportDir;

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
}
