package fi.poltsi.vempain.file.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.entity.AudioFileEntity;
import fi.poltsi.vempain.file.entity.BinaryFileEntity;
import fi.poltsi.vempain.file.entity.DataFileEntity;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.entity.ExecutableFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FontFileEntity;
import fi.poltsi.vempain.file.entity.IconFileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.InteractiveFileEntity;
import fi.poltsi.vempain.file.entity.MetadataEntity;
import fi.poltsi.vempain.file.entity.MusicFileEntity;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileTagRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.tools.SampleFiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests (ITC) that scan real files of every type ExifTool can describe without errors through {@link DirectoryProcessorService}: the typed entity,
 * tags, metadata rows, GPS location and export derivatives end up in the database. Archives and vector files are left out because ExifTool reports no
 * uncompressed size or layer count for them and the extractors fail on the empty value; those branches are covered by
 * {@code DirectoryProcessorServiceExtendedUTC}.
 */
@SpringBootTest(properties = {
    "vempain.app.frontend-url=http://localhost:3000",
    "vempain.original-root-directory=/tmp",
    "vempain.export-root-directory=/tmp",
    "vempain.generate-missing-thumbnails.batch-size=10",
    "vempain.generate-missing-thumbnails.thumb-image-quality=0.5",
    "vempain.generate-missing-thumbnails.thumb-image-size=100" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DirectoryProcessorServiceFileTypesITC {

    @Autowired
    private FileScannerService fileScannerService;
    @Autowired
    private DirectoryProcessorService directoryProcessorService;
    @Autowired
    private FileRepository fileRepository;
    @Autowired
    private ExportFileRepository exportFileRepository;
    @Autowired
    private MetadataRepository metadataRepository;
    @Autowired
    private FileTagRepository fileTagRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String suffix;
    private Path scanDir;
    private String scanDirName;
    private List<Path> directories;

    @BeforeEach
    void setUp() throws IOException {
        var principal = new UserDetailsImpl(1L, "admin", "Admin", "admin@nohost.nodomain", "Disabled", Set.of(), List.of());
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        suffix = UUID.randomUUID()
            .toString()
            .substring(0, 8);
        scanDirName = "itc-types-" + suffix;
        scanDir = Files.createDirectories(Path.of("/tmp", scanDirName));
        directories = new ArrayList<>(List.of(scanDir));
    }

    @AfterEach
    void cleanUp() throws IOException {
        SecurityContextHolder.clearContext();
        for (var directory : directories) {
            jdbcTemplate.update("DELETE FROM export_files WHERE file_path = ?", "/" + directory.getFileName());
            var aclIds = jdbcTemplate.queryForList("SELECT acl_id FROM files WHERE file_path = ?", Long.class, "/" + directory.getFileName());
            jdbcTemplate.update("DELETE FROM files WHERE file_path = ?", "/" + directory.getFileName());
            for (var aclId : aclIds) {
                // The acl_acl_id_seq of the test schema is not advanced past the seeded administrator ACL, so the first ACL a scan
                // creates can share acl_id 1 with it; that reserved ACL must survive the cleanup
                jdbcTemplate.update("DELETE FROM acl WHERE acl_id = ? AND acl_id <> ?", aclId, fi.poltsi.vempain.auth.api.Constants.ADMIN_ID);
            }
            jdbcTemplate.update("DELETE FROM file_group WHERE path = ?", "/" + directory.getFileName());
            if (Files.exists(directory)) {
                try (var stream = Files.walk(directory)) {
                    stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            }
        }
        jdbcTemplate.update("DELETE FROM tags WHERE tag_name LIKE ?", "itc-" + suffix + "%");
    }

    @Test
    void scanStoresTheTypedEntitiesTagsMetadataAndGpsOfRealFiles() throws Exception {
        var documentId = "itc-doc-" + suffix;
        var image = SampleFiles.jpeg(scanDir.resolve("photo.jpg"));
        SampleFiles.exiftool(image, "-GPSLatitude=60.17", "-GPSLatitudeRef=N", "-GPSLongitude=24.94", "-GPSLongitudeRef=E", "-GPSAltitude=10",
                "-XMP-photoshop:City=Helsinki", "-XMP-dc:Subject=itc-" + suffix + "-alpha", "-XMP-dc:Subject=itc-" + suffix + "-beta",
                "-XMP-dc:Description=ITC caption", "-DateTimeOriginal=2024:01:02 03:04:05", "-XMP-xmpMM:OriginalDocumentID=" + documentId,
                "-IFD0:Artist=ITC Artist");
        writeResource("/test_mp3_id3v23.mp3", scanDir.resolve("track.mp3"));
        SampleFiles.wav(scanDir.resolve("tone.wav"));
        SampleFiles.pdf(scanDir.resolve("doc.pdf"));
        SampleFiles.ico(scanDir.resolve("icon.ico"));
        Files.writeString(scanDir.resolve("data.json"), "{\"a\":1}");
        Files.writeString(scanDir.resolve("script.sh"), "#!/bin/sh\necho hi\n");
        var raw = new byte[256];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) i;
        }
        Files.write(scanDir.resolve("raw.bin"), raw);
        SampleFiles.mp4(scanDir.resolve("clip.mp4"));
        Files.write(scanDir.resolve("movie.dir"), "RIFX\0\0\0\0shockwave".getBytes(StandardCharsets.US_ASCII));

        var response = fileScannerService.scanDirectories(new ScanRequest("/" + scanDirName, null))
            .getScanOriginalResponse();

        assertThat(response.getErrorMessage()).isEmpty();
        assertThat(response.getFailedFiles()).isEmpty();
        assertThat(response.getScannedFilesCount()).isEqualTo(10L);
        assertThat(response.getNewFilesCount()).isEqualTo(10L);
        assertThat(response.isSuccess()).isTrue();

        var photo = (ImageFileEntity) stored("photo.jpg", FileTypeEnum.IMAGE);
        assertThat(photo.getWidth()).isEqualTo(16);
        assertThat(photo.getHeight()).isEqualTo(16);
        assertThat(photo.getDescription()).isEqualTo("ITC caption");
        assertThat(photo.getCreatorName()).isEqualTo("ITC Artist");
        assertThat(photo.getOriginalDocumentId()).isEqualTo(documentId);
        assertThat(photo.getOriginalDatetime()).isNotNull();
        assertThat(photo.getGpsLocation()).isNotNull();
        assertThat(photo.getGpsLocation()
            .getCity()).isEqualTo("Helsinki");
        assertThat(photo.getGpsLocation()
            .getLatitudeRef()).isEqualTo('N');
        assertThat(photo.getGpsLocation()
            .getAltitude()).isEqualTo(10.0);
        assertThat(fileTagRepository.findByFileId(photo.getId())).hasSize(2);
        assertThat(metadataRepository.findByFile(photo)).extracting(MetadataEntity::getMetadataGroup)
            .contains("XMP-dc", "GPS", "File", "Composite");

        var track = (MusicFileEntity) stored("track.mp3", FileTypeEnum.MUSIC);
        assertThat(track.getArtist()).isEqualTo("Test Artist");
        assertThat(track.getAlbum()).isEqualTo("Test Album");
        assertThat(track.getTrackNumber()).isEqualTo(4);
        assertThat(track.getTrackTotal()).isEqualTo(7);
        assertThat(track.getYear()).isEqualTo(1990);
        assertThat(track.getDuration()
            .toMillis()).isGreaterThan(0L);

        var tone = (AudioFileEntity) stored("tone.wav", FileTypeEnum.AUDIO);
        assertThat(tone).isNotInstanceOf(MusicFileEntity.class);
        assertThat(tone.getSampleRate()).isEqualTo(8000);
        assertThat(tone.getDuration()).isEqualTo(java.time.Duration.ofSeconds(2));
        assertThat(tone.getBitRate()).isGreaterThan(0);

        var document = (DocumentFileEntity) stored("doc.pdf", FileTypeEnum.DOCUMENT);
        assertThat(document.getPageCount()).isEqualTo(1);
        assertThat(document.getFormat()).isEqualTo("PDF");

        var icon = (IconFileEntity) stored("icon.ico", FileTypeEnum.ICON);
        assertThat(icon.getWidth()).isEqualTo(16);
        assertThat(icon.getHeight()).isEqualTo(16);
        assertThat(icon.getIsScalable()).isFalse();

        assertThat(((DataFileEntity) stored("data.json", FileTypeEnum.DATA)).getDataStructure()).isEqualTo("JSON");
        var script = (ExecutableFileEntity) stored("script.sh", FileTypeEnum.EXECUTABLE);
        assertThat(script.isScript()).isTrue();
        assertThat(script.getOperatingSystems()).containsExactly("OTHER");
        var binary = (BinaryFileEntity) stored("raw.bin", FileTypeEnum.BINARY);
        assertThat(binary.getSoftwareName()).isNull();
        assertThat(binary.getSoftwareMajorVersion()).isNull();

        var clip = (VideoFileEntity) stored("clip.mp4", FileTypeEnum.VIDEO);
        assertThat(clip.getWidth()).isEqualTo(64);
        assertThat(clip.getHeight()).isEqualTo(48);
        assertThat(clip.getFrameRate()).isEqualTo(12.0);
        assertThat(clip.getCodec()).isEqualTo("mpeg4");
        assertThat(clip.getDuration()
            .toMillis()).isGreaterThan(0L);

        assertThat(((InteractiveFileEntity) stored("movie.dir", FileTypeEnum.INTERACTIVE)).getTechnology()).isEqualTo("SHOCKWAVE");
    }

    @Test
    void scanStoresFontFilesWhenAFontIsAvailable() throws Exception {
        var systemFont = SampleFiles.systemFont();
        Assumptions.assumeTrue(systemFont.isPresent(), "No TrueType font installed on this host");
        Files.copy(systemFont.get(), scanDir.resolve("font.ttf"));

        var response = fileScannerService.scanDirectories(new ScanRequest("/" + scanDirName, null))
            .getScanOriginalResponse();

        assertThat(response.getNewFilesCount()).isEqualTo(1L);
        var font = (FontFileEntity) stored("font.ttf", FileTypeEnum.FONT);
        assertThat(font.getFontFamily()).isNotBlank();
    }

    @Test
    void rescanReplacesAChangedFileAndSkipsAnUnchangedOne() throws Exception {
        var image = SampleFiles.jpeg(scanDir.resolve("changing.jpg"));
        SampleFiles.exiftool(image, "-XMP-dc:Description=first");
        var request = new ScanRequest("/" + scanDirName, null);

        var first = fileScannerService.scanDirectories(request)
            .getScanOriginalResponse();
        assertThat(first.getNewFilesCount()).isEqualTo(1L);
        var firstEntity = stored("changing.jpg", FileTypeEnum.IMAGE);
        assertThat(firstEntity.getDescription()).isEqualTo("first");

        var unchanged = fileScannerService.scanDirectories(request)
            .getScanOriginalResponse();
        assertThat(unchanged.getScannedFilesCount()).isEqualTo(1L);
        assertThat(unchanged.getNewFilesCount()).isZero();

        SampleFiles.exiftool(image, "-XMP-dc:Description=second");
        var changed = fileScannerService.scanDirectories(request)
            .getScanOriginalResponse();
        assertThat(changed.getNewFilesCount()).isEqualTo(1L);
        var secondEntity = stored("changing.jpg", FileTypeEnum.IMAGE);
        assertThat(secondEntity.getId()).isNotEqualTo(firstEntity.getId());
        assertThat(secondEntity.getDescription()).isEqualTo("second");
        assertThat(secondEntity.getSha256sum()).isNotEqualTo(firstEntity.getSha256sum());
    }

    @Test
    void exportScanRegistersDerivativesSkipsKnownOnesAndReportsOrphans() throws Exception {
        var documentId = "itc-export-doc-" + suffix;
        var original = SampleFiles.jpeg(scanDir.resolve("original.jpg"));
        SampleFiles.exiftool(original, "-XMP-xmpMM:OriginalDocumentID=" + documentId);
        fileScannerService.scanDirectories(new ScanRequest("/" + scanDirName, null));
        var originalEntity = stored("original.jpg", FileTypeEnum.IMAGE);

        var exportDirName = "itc-export-" + suffix;
        var exportDir = Files.createDirectories(Path.of("/tmp", exportDirName));
        directories.add(exportDir);
        var derivative = SampleFiles.jpeg(exportDir.resolve("original.jpg"));
        SampleFiles.exiftool(derivative, "-XMP-xmpMM:OriginalDocumentID=" + documentId);
        var orphan = SampleFiles.jpeg(exportDir.resolve("orphan.jpg"));
        SampleFiles.exiftool(orphan, "-XMP-xmpMM:OriginalDocumentID=itc-nobody-" + suffix);
        var request = new ScanRequest(null, "/" + exportDirName);

        var first = fileScannerService.scanDirectories(request)
            .getScanExportResponse();
        assertThat(first.getErrorMessage()).isEmpty();
        assertThat(first.getSuccessfulFiles()).hasSize(1);
        assertThat(first.getSuccessfulFiles()
            .getFirst()
            .getFileId()).isEqualTo(originalEntity.getId());
        assertThat(first.getFailedFiles()).containsExactly("orphan.jpg");
        var export = exportFileRepository.findByFileId(originalEntity.getId())
            .orElseThrow();
        assertThat(export.getFilePath()).isEqualTo("/" + exportDirName);
        assertThat(export.getMimetype()).isEqualTo("image/jpeg");
        assertThat(export.getOriginalDocumentId()).isEqualTo(documentId);

        var unchanged = fileScannerService.scanDirectories(request)
            .getScanExportResponse();
        assertThat(unchanged.getSuccessfulFiles()).isEmpty();
        assertThat(unchanged.getFailedFiles()).containsExactly("orphan.jpg");

        SampleFiles.exiftool(derivative, "-XMP-dc:Description=resized again");
        var changed = fileScannerService.scanDirectories(request)
            .getScanExportResponse();
        assertThat(changed.getSuccessfulFiles()).hasSize(1);
        var replaced = exportFileRepository.findByFileId(originalEntity.getId())
            .orElseThrow();
        assertThat(replaced.getId()).isNotEqualTo(export.getId());
        assertThat(replaced.getSha256sum()).isNotEqualTo(export.getSha256sum());
    }

    @Test
    void refreshExistingOriginalFileRereadsTheFileAndRebuildsItsRows() throws Exception {
        var image = SampleFiles.jpeg(scanDir.resolve("refresh.jpg"));
        SampleFiles.exiftool(image, "-XMP-dc:Description=before", "-XMP-dc:Subject=itc-" + suffix + "-old");
        fileScannerService.scanDirectories(new ScanRequest("/" + scanDirName, null));
        var entity = stored("refresh.jpg", FileTypeEnum.IMAGE);
        assertThat(entity.getDescription()).isEqualTo("before");
        var rowsBefore = metadataRepository.findByFile(entity);

        SampleFiles.exiftool(image, "-XMP-dc:Description=after", "-XMP-dc:Subject=itc-" + suffix + "-new");
        assertThat(directoryProcessorService.refreshExistingOriginalFile(entity, image)).isTrue();

        var refreshed = stored("refresh.jpg", FileTypeEnum.IMAGE);
        assertThat(refreshed.getId()).isEqualTo(entity.getId());
        assertThat(refreshed.getDescription()).isEqualTo("after");
        var rowsAfter = metadataRepository.findByFile(refreshed);
        assertThat(rowsAfter).isNotEmpty();
        assertThat(rowsAfter).extracting(MetadataEntity::getId)
            .doesNotContainAnyElementsOf(rowsBefore.stream()
                .map(MetadataEntity::getId)
                .toList());
        assertThat(rowsAfter).anySatisfy(row -> {
            assertThat(row.getMetadataKey()).isEqualTo("Description");
            assertThat(row.getMetadataValue()).isEqualTo("after");
        });
        assertThat(jdbcTemplate.queryForList("SELECT t.tag_name FROM file_tags ft JOIN tags t ON t.id = ft.tag_id WHERE ft.file_id = ?", String.class,
                refreshed.getId()))
            .containsExactly("itc-" + suffix + "-new");
    }

    private FileEntity stored(String filename, FileTypeEnum type) {
        var entity = fileRepository.findByFilePathAndFilename("/" + scanDirName, filename)
            .orElseThrow(() -> new AssertionError("Not stored: " + filename));
        assertThat(entity.getFileType()).as(filename)
            .isEqualTo(type);
        return entity;
    }

    private void writeResource(String resource, Path target) throws IOException {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream(resource), resource)) {
            Files.write(target, stream.readAllBytes());
        }
    }
}
