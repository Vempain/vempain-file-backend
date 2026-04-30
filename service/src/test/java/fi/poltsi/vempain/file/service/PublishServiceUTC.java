package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.feign.VempainAdminTokenProvider;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests (UTC) for {@link PublishService} methods that can be tested without full Spring context.
 */
@ExtendWith(MockitoExtension.class)
class PublishServiceUTC {

    @Mock
    private FileGroupRepository fileGroupRepository;
    @Mock
    private ExportFileRepository exportFileRepository;
    @Mock
    private MetadataRepository metadataRepository;
    @Mock
    private VempainAdminService vempainAdminService;
    @Mock
    private TagService tagService;
    @Mock
    private LocationService locationService;
    @Mock
    private VempainAdminTokenProvider vempainAdminTokenProvider;
    @Mock
    private ImageTool imageTool;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private PublishProgressStore progressStore;

    @InjectMocks
    private PublishService publishService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(publishService, "exportRootDirectory", tempDir.toString());
        ReflectionTestUtils.setField(publishService, "exportFileType", "jpeg");
        ReflectionTestUtils.setField(publishService, "siteImageSize", 1200);
    }

    // ------------------------------------------------------------------
    // countFilesInGroup
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("countFilesInGroup")
    class CountFilesInGroup {

        @Test
        void returnsCount() {
            when(fileGroupRepository.countById(5L)).thenReturn(3L);
            assertThat(publishService.countFilesInGroup(5L)).isEqualTo(3L);
        }

        @Test
        void returnsZeroWhenNoFiles() {
            when(fileGroupRepository.countById(99L)).thenReturn(0L);
            assertThat(publishService.countFilesInGroup(99L)).isEqualTo(0L);
        }
    }

    // ------------------------------------------------------------------
    // republishSiteFile
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("republishSiteFile")
    class RepublishSiteFile {

        @Test
        void nullFileEntity_returnsFalse() {
            assertFalse(publishService.republishSiteFile(null));
        }

        @Test
        void noExportedFile_returnsFalse() {
            var mockFile = mock(FileEntity.class);
            when(mockFile.getId()).thenReturn(1L);
            when(exportFileRepository.findByFileId(1L)).thenReturn(Optional.empty());

            assertFalse(publishService.republishSiteFile(mockFile));
        }

        @Test
        void exportFileExistsOnDisk_nonImage_success(@TempDir Path tempDir2) throws IOException {
            ReflectionTestUtils.setField(publishService, "exportRootDirectory", tempDir2.toString());

            // Create a real export file
            var subDir = tempDir2.resolve("sub");
            Files.createDirectories(subDir);
            var exportFile = subDir.resolve("test.jpg");
            // Write minimal JPEG bytes
            Files.write(exportFile, getMinimalJpegBytes());

            var exportEntity = ExportFileEntity.builder()
                                               .id(1L)
                                               .filePath("/sub")
                                               .filename("test.jpg")
                                               .build();
            when(exportFileRepository.findByFileId(42L)).thenReturn(Optional.of(exportEntity));

            var mockFileEntity = mock(FileEntity.class);
            when(mockFileEntity.getId()).thenReturn(42L);
            when(mockFileEntity.getFileType()).thenReturn(FileTypeEnum.BINARY);
            when(mockFileEntity.getFilePath()).thenReturn("/sub");
            when(mockFileEntity.getFilename()).thenReturn("test.jpg");
            when(mockFileEntity.getDescription()).thenReturn("Test");
            when(mockFileEntity.getGpsLocation()).thenReturn(null);
            when(metadataRepository.findByFile(mockFileEntity)).thenReturn(List.of());
            when(tagService.getTagRequestsByFileId(42L)).thenReturn(List.of());
            when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(null);

            var result = publishService.republishSiteFile(mockFileEntity);
            // For BINARY type, should attempt to upload and succeed
            assertTrue(result);
        }

        @Test
        void exportFileDoesNotExistOnDisk_returnsFalse() {
            var exportEntity = ExportFileEntity.builder()
                                               .id(1L)
                                               .filePath("/nonexistent")
                                               .filename("missing.jpg")
                                               .build();
            when(exportFileRepository.findByFileId(99L)).thenReturn(Optional.of(exportEntity));

            var mockFileEntity = mock(FileEntity.class);
            when(mockFileEntity.getId()).thenReturn(99L);

            assertFalse(publishService.republishSiteFile(mockFileEntity));
        }

        @Test
        void documentFile_setsPages(@TempDir Path tempDir3) throws IOException {
            ReflectionTestUtils.setField(publishService, "exportRootDirectory", tempDir3.toString());

            var subDir = tempDir3.resolve("docs");
            Files.createDirectories(subDir);
            var docFile = subDir.resolve("doc.pdf");
            Files.write(docFile, "%PDF-1.4\n".getBytes());

            var exportEntity = ExportFileEntity.builder()
                                               .id(2L)
                                               .filePath("/docs")
                                               .filename("doc.pdf")
                                               .build();
            when(exportFileRepository.findByFileId(55L)).thenReturn(Optional.of(exportEntity));

            var mockDocEntity = mock(DocumentFileEntity.class);
            when(mockDocEntity.getId()).thenReturn(55L);
            when(mockDocEntity.getFileType()).thenReturn(FileTypeEnum.DOCUMENT);
            when(mockDocEntity.getFilePath()).thenReturn("/docs");
            when(mockDocEntity.getFilename()).thenReturn("doc.pdf");
            when(mockDocEntity.getDescription()).thenReturn(null);
            when(mockDocEntity.getGpsLocation()).thenReturn(null);
            when(mockDocEntity.getPageCount()).thenReturn(5);
            when(metadataRepository.findByFile(mockDocEntity)).thenReturn(List.of());
            when(tagService.getTagRequestsByFileId(55L)).thenReturn(List.of());
            when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(null);

            var result = publishService.republishSiteFile(mockDocEntity);
            assertTrue(result);
        }
    }

    private static byte[] getMinimalJpegBytes() {
        return new byte[]{
            (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0x00, 0x10, 0x4A, 0x46,
            0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            (byte)0xFF, (byte)0xDB, 0x00, 0x43, 0x00, 0x08, 0x06, 0x06, 0x07, 0x06,
            0x05, 0x08, 0x07, 0x07, 0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D,
            0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12, 0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F,
            0x1E, 0x1D, 0x1A, 0x1C, 0x1C, 0x20, 0x24, 0x2E, 0x27, 0x20, 0x22, 0x2C,
            0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C, 0x30, 0x31, 0x34, 0x34, 0x34,
            0x1F, 0x27, 0x39, 0x3D, 0x38, 0x32, 0x3C, 0x2E, 0x33, 0x34, 0x32,
            (byte)0xFF, (byte)0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01,
            0x01, 0x11, 0x00, (byte)0xFF, (byte)0xC4, 0x00, 0x1F, 0x00, 0x00, 0x01,
            0x05, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
            0x0A, 0x0B, (byte)0xFF, (byte)0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00,
            0x3F, 0x00, (byte)0xFB, (byte)0xD2, (byte)0xFF, (byte)0xD9
        };
    }
}
