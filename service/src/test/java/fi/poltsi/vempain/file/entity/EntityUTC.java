package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.service.VempainMultipartFile;
import fi.poltsi.vempain.file.tools.FileTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests (UTC) for entity and tool classes:
 * TagEntity, ExportFileEntity, FileGroupEntity, DurationSecondsConverter,
 * VempainMultipartFile, FileTool, FileGroupSummaryRow.
 */
class EntityUTC {

    // -------------------------------------------------------------------------
    // TagEntity
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("TagEntity")
    class TagEntityTests {

        @Test
        void toResponse_mapsAllFields() {
            var tag = TagEntity.builder()
                               .id(1L)
                               .tagName("nature")
                               .tagNameDe("Natur")
                               .tagNameEn("nature")
                               .tagNameEs("naturaleza")
                               .tagNameFi("luonto")
                               .tagNameSv("natur")
                               .build();

            var response = tag.toResponse();

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getTagName()).isEqualTo("nature");
            assertThat(response.getTagNameDe()).isEqualTo("Natur");
            assertThat(response.getTagNameEn()).isEqualTo("nature");
            assertThat(response.getTagNameEs()).isEqualTo("naturaleza");
            assertThat(response.getTagNameFi()).isEqualTo("luonto");
            assertThat(response.getTagNameSv()).isEqualTo("natur");
        }

        @Test
        void toRequest_mapsAllFields() {
            var tag = TagEntity.builder()
                               .id(2L)
                               .tagName("urban")
                               .tagNameDe("urban")
                               .tagNameEn("urban")
                               .tagNameEs("urbano")
                               .tagNameFi("urbaani")
                               .tagNameSv("urban")
                               .build();

            var request = tag.toRequest();

            assertThat(request.getId()).isEqualTo(2L);
            assertThat(request.getTagName()).isEqualTo("urban");
            assertThat(request.getTagNameDe()).isEqualTo("urban");
            assertThat(request.getTagNameEn()).isEqualTo("urban");
            assertThat(request.getTagNameEs()).isEqualTo("urbano");
            assertThat(request.getTagNameFi()).isEqualTo("urbaani");
            assertThat(request.getTagNameSv()).isEqualTo("urban");
        }

        @Test
        void defaultFilesSet_isEmptyHashSet() {
            var tag = TagEntity.builder().tagName("test").build();
            assertThat(tag.getFiles()).isNotNull().isEmpty();
        }

        @Test
        void toResponse_withNullOptionalFields() {
            var tag = TagEntity.builder().id(3L).tagName("minimal").build();
            var response = tag.toResponse();
            assertThat(response.getId()).isEqualTo(3L);
            assertThat(response.getTagName()).isEqualTo("minimal");
            assertNull(response.getTagNameDe());
            assertNull(response.getTagNameEn());
        }
    }

    // -------------------------------------------------------------------------
    // ExportFileEntity
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("ExportFileEntity")
    class ExportFileEntityTests {

        @Test
        void toResponse_mapsAllFields() {
            var mockFile = Mockito.mock(FileEntity.class);
            Mockito.when(mockFile.getId()).thenReturn(42L);

            var now = Instant.now();
            var entity = ExportFileEntity.builder()
                                         .id(10L)
                                         .file(mockFile)
                                         .filename("export.jpg")
                                         .filePath("/exports/2024")
                                         .mimetype("image/jpeg")
                                         .filesize(12345L)
                                         .originalDocumentId("doc-id-123")
                                         .sha256sum("abcdef1234567890")
                                         .created(now)
                                         .build();

            var response = entity.toResponse();

            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getFile_id()).isEqualTo(42L);
            assertThat(response.getFilename()).isEqualTo("export.jpg");
            assertThat(response.getFilePath()).isEqualTo("/exports/2024");
            assertThat(response.getMimetype()).isEqualTo("image/jpeg");
            assertThat(response.getFilesize()).isEqualTo(12345L);
            assertThat(response.getOriginalDocumentId()).isEqualTo("doc-id-123");
            assertThat(response.getSha256sum()).isEqualTo("abcdef1234567890");
            assertThat(response.getCreated()).isEqualTo(now);
        }

        @Test
        void toResponse_withNullOriginalDocumentId() {
            var mockFile = Mockito.mock(FileEntity.class);
            Mockito.when(mockFile.getId()).thenReturn(1L);

            var entity = ExportFileEntity.builder()
                                         .id(1L)
                                         .file(mockFile)
                                         .filename("file.jpg")
                                         .filePath("/path")
                                         .mimetype("image/jpeg")
                                         .filesize(100L)
                                         .sha256sum("abc")
                                         .created(Instant.now())
                                         .build();

            var response = entity.toResponse();
            assertNull(response.getOriginalDocumentId());
        }
    }

    // -------------------------------------------------------------------------
    // FileGroupEntity
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("FileGroupEntity")
    class FileGroupEntityTests {

        @Test
        void toResponse_mapsAllFields() {
            var entity = FileGroupEntity.builder()
                                        .id(5L)
                                        .path("/photos/2024")
                                        .groupName("Summer")
                                        .description("Summer photos")
                                        .files(new ArrayList<>())
                                        .build();

            var response = entity.toResponse();

            assertThat(response.getId()).isEqualTo(5L);
            assertThat(response.getPath()).isEqualTo("/photos/2024");
            assertThat(response.getGroupName()).isEqualTo("Summer");
            assertThat(response.getDescription()).isEqualTo("Summer photos");
            assertThat(response.getFiles()).isEmpty();
        }

        @Test
        void toResponse_withNullFiles() {
            var entity = FileGroupEntity.builder()
                                        .id(6L)
                                        .path("/path")
                                        .groupName("Group")
                                        .description("Desc")
                                        .build();
            entity.setFiles(null);
            var response = entity.toResponse();
            assertThat(response.getFiles()).isEmpty();
        }

        @Test
        void replaceFiles_addsNewFiles() {
            var entity = FileGroupEntity.builder()
                                        .id(7L)
                                        .path("/path")
                                        .groupName("G")
                                        .description("")
                                        .files(new ArrayList<>())
                                        .build();

            var mockFile = Mockito.mock(FileEntity.class);
            Mockito.when(mockFile.getFileGroups()).thenReturn(new HashSet<>());

            entity.replaceFiles(List.of(mockFile));

            assertThat(entity.getFiles()).hasSize(1);
        }

        @Test
        void replaceFiles_withNull_resultsInEmptyList() {
            var entity = FileGroupEntity.builder()
                                        .id(8L)
                                        .path("/path")
                                        .groupName("G")
                                        .description("")
                                        .files(new ArrayList<>())
                                        .build();

            entity.replaceFiles(null);
            assertThat(entity.getFiles()).isEmpty();
        }

        @Test
        void replaceFiles_removesOldFiles() {
            var mockOldFile = Mockito.mock(FileEntity.class);
            var groups = new HashSet<FileGroupEntity>();
            Mockito.when(mockOldFile.getFileGroups()).thenReturn(groups);

            var files = new ArrayList<FileEntity>();
            files.add(mockOldFile);

            var entity = FileGroupEntity.builder()
                                        .id(9L)
                                        .path("/path")
                                        .groupName("G")
                                        .description("")
                                        .files(files)
                                        .build();

            entity.replaceFiles(List.of());
            assertThat(entity.getFiles()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // DurationSecondsConverter
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("DurationSecondsConverter")
    class DurationSecondsConverterTests {

        private final DurationSecondsConverter converter = new DurationSecondsConverter();

        @Test
        void convertToDatabaseColumn_null_returnsZero() {
            assertThat(converter.convertToDatabaseColumn(null)).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void convertToDatabaseColumn_negative_returnsZero() {
            assertThat(converter.convertToDatabaseColumn(Duration.ofSeconds(-5))).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        void convertToDatabaseColumn_normal_returnsSeconds() {
            assertThat(converter.convertToDatabaseColumn(Duration.ofSeconds(120))).isEqualTo(BigDecimal.valueOf(120));
        }

        @Test
        void convertToDatabaseColumn_exceedsMax_clampsTo99999() {
            assertThat(converter.convertToDatabaseColumn(Duration.ofSeconds(100_000))).isEqualTo(BigDecimal.valueOf(99_999));
        }

        @Test
        void convertToEntityAttribute_null_returnsZero() {
            assertThat(converter.convertToEntityAttribute(null)).isEqualTo(Duration.ZERO);
        }

        @Test
        void convertToEntityAttribute_negative_returnsZero() {
            assertThat(converter.convertToEntityAttribute(BigDecimal.valueOf(-1))).isEqualTo(Duration.ZERO);
        }

        @Test
        void convertToEntityAttribute_normal_returnsDuration() {
            assertThat(converter.convertToEntityAttribute(BigDecimal.valueOf(300))).isEqualTo(Duration.ofSeconds(300));
        }

        @Test
        void roundTrip_preservesValue() {
            var original = Duration.ofSeconds(1800);
            var db = converter.convertToDatabaseColumn(original);
            var restored = converter.convertToEntityAttribute(db);
            assertThat(restored).isEqualTo(original);
        }
    }

    // -------------------------------------------------------------------------
    // VempainMultipartFile
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("VempainMultipartFile")
    class VempainMultipartFileTests {

        @Test
        void getName_returnsFilename(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("test.jpg");
            Files.writeString(file, "content");
            var mf = VempainMultipartFile.builder().path(file).contentType("image/jpeg").build();
            assertThat(mf.getName()).isEqualTo("test.jpg");
        }

        @Test
        void getOriginalFilename_returnsFilename(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("original.png");
            Files.writeString(file, "content");
            var mf = VempainMultipartFile.builder().path(file).contentType("image/png").build();
            assertThat(mf.getOriginalFilename()).isEqualTo("original.png");
        }

        @Test
        void getContentType_returnsConfigured(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("data.txt");
            Files.writeString(file, "hello");
            var mf = VempainMultipartFile.builder().path(file).contentType("text/plain").build();
            assertThat(mf.getContentType()).isEqualTo("text/plain");
        }

        @Test
        void getSize_returnsFileSize(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("sized.txt");
            Files.writeString(file, "hello");
            var mf = VempainMultipartFile.builder().path(file).contentType("text/plain").build();
            assertThat(mf.getSize()).isEqualTo(5L);
        }

        @Test
        void isEmpty_falseForNonEmpty(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("nonempty.txt");
            Files.writeString(file, "data");
            var mf = VempainMultipartFile.builder().path(file).contentType("text/plain").build();
            assertFalse(mf.isEmpty());
        }

        @Test
        void getBytes_returnsContent(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("bytes.txt");
            Files.writeString(file, "ABC");
            var mf = VempainMultipartFile.builder().path(file).contentType("text/plain").build();
            assertThat(mf.getBytes()).isEqualTo("ABC".getBytes());
        }

        @Test
        void getInputStream_returnsStream(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("stream.txt");
            Files.writeString(file, "stream content");
            var mf = VempainMultipartFile.builder().path(file).contentType("text/plain").build();
            try (var is = mf.getInputStream()) {
                assertThat(is.readAllBytes()).isEqualTo("stream content".getBytes());
            }
        }

        @Test
        void transferToPath_copiesToDestination(@TempDir Path tempDir) throws IOException {
            var src = tempDir.resolve("source.txt");
            var dst = tempDir.resolve("dest.txt");
            Files.writeString(src, "transfer test");
            var mf = VempainMultipartFile.builder().path(src).contentType("text/plain").build();
            mf.transferTo(dst);
            assertThat(Files.readString(dst)).isEqualTo("transfer test");
        }

        @Test
        void transferToFile_copiesToDestination(@TempDir Path tempDir) throws IOException {
            var src = tempDir.resolve("source2.txt");
            var dst = tempDir.resolve("dest2.txt");
            Files.writeString(src, "file transfer");
            var mf = VempainMultipartFile.builder().path(src).contentType("text/plain").build();
            mf.transferTo(dst.toFile());
            assertThat(Files.readString(dst)).isEqualTo("file transfer");
        }
    }

    // -------------------------------------------------------------------------
    // FileTool
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("FileTool")
    class FileToolTests {

        @Test
        void computeSha256_returnsHexString(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("hashme.txt");
            Files.writeString(file, "hello world");
            var sha = FileTool.computeSha256(file.toFile());
            assertNotNull(sha);
            assertThat(sha).hasSize(64);
        }

        @Test
        void computeSha256_differentContent_differentHash(@TempDir Path tempDir) throws IOException {
            var file1 = tempDir.resolve("file1.txt");
            var file2 = tempDir.resolve("file2.txt");
            Files.writeString(file1, "content1");
            Files.writeString(file2, "content2");
            var sha1 = FileTool.computeSha256(file1.toFile());
            var sha2 = FileTool.computeSha256(file2.toFile());
            assertThat(sha1).isNotEqualTo(sha2);
        }

        @Test
        void computeSha256_nonExistentFile_returnsNull() {
            var file = new java.io.File("/non/existent/file.txt");
            var sha = FileTool.computeSha256(file);
            assertNull(sha);
        }
    }

    // -------------------------------------------------------------------------
    // FileGroupSummaryRow
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("FileGroupSummaryRow")
    class FileGroupSummaryRowTests {

        @Test
        void construct_storesValues() {
            var row = new fi.poltsi.vempain.file.repository.FileGroupSummaryRow(1L, "path", "group", "description", 5L);
            assertThat(row.id()).isEqualTo(1L);
            assertThat(row.path()).isEqualTo("path");
            assertThat(row.groupName()).isEqualTo("group");
            assertThat(row.description()).isEqualTo("description");
            assertThat(row.fileCount()).isEqualTo(5L);
        }
    }
}
