package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import fi.poltsi.vempain.file.api.request.ScanRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests (ITC) for {@link DirectoryProcessorService}.
 *
 * <p>Uses a full Spring context with Testcontainers PostgreSQL (configured via
 * {@code src/test/resources/application.yaml}) and a real security principal so that
 * {@code AuthTools.getCurrentUserId()} resolves to the seed user with id=1.</p>
 */
@SpringBootTest(properties = {
        "vempain.app.frontend-url=http://localhost:3000",
        "vempain.original-root-directory=/tmp",
        "vempain.export-root-directory=/tmp"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DirectoryProcessorServiceITC {

    @Autowired
    private FileScannerService fileScannerService;

    @Value("${vempain.original-root-directory}")
    private String originalRootDirectory;

    private Path testScanDir;
    private String relativeScanDirName;

    /** Minimal valid 1×1 JPEG bytes. */
    private static final byte[] MINIMAL_JPEG = {
            (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0x00,0x10,0x4A,0x46,0x49,0x46,0x00,0x01,
            0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00,(byte)0xFF,(byte)0xDB,0x00,0x43,0x00,0x08,
            0x06,0x06,0x07,0x06,0x05,0x08,0x07,0x07,0x07,0x09,0x09,0x08,0x0A,0x0C,0x14,0x0D,
            0x0C,0x0B,0x0B,0x0C,0x19,0x12,0x13,0x0F,0x14,0x1D,0x1A,0x1F,0x1E,0x1D,0x1A,0x1C,
            0x1C,0x20,0x24,0x2E,0x27,0x20,0x22,0x2C,0x23,0x1C,0x1C,0x28,0x37,0x29,0x2C,0x30,
            0x31,0x34,0x34,0x34,0x1F,0x27,0x39,0x3D,0x38,0x32,0x3C,0x2E,0x33,0x34,0x32,
            (byte)0xFF,(byte)0xC0,0x00,0x0B,0x08,0x00,0x01,0x00,0x01,0x01,0x01,0x11,0x00,
            (byte)0xFF,(byte)0xC4,0x00,0x1F,0x00,0x00,0x01,0x05,0x01,0x01,0x01,0x01,0x01,0x01,
            0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,
            0x09,0x0A,0x0B,(byte)0xFF,(byte)0xC4,0x00,(byte)0xB5,0x10,0x00,0x02,0x01,0x03,
            0x03,0x02,0x04,0x03,0x05,0x05,0x04,0x04,0x00,0x00,0x01,0x7D,0x01,0x02,0x03,0x00,
            0x04,0x11,0x05,0x12,0x21,0x31,0x41,0x06,0x13,0x51,0x61,0x07,0x22,0x71,0x14,0x32,
            (byte)0x81,(byte)0x91,(byte)0xA1,0x08,0x23,0x42,(byte)0xB1,(byte)0xC1,0x15,0x52,
            (byte)0xD1,(byte)0xF0,0x24,0x33,0x62,0x72,(byte)0x82,0x09,0x0A,0x16,0x17,0x18,
            0x19,0x1A,0x25,0x26,0x27,0x28,0x29,0x2A,0x34,0x35,0x36,0x37,0x38,0x39,0x3A,
            0x43,0x44,0x45,0x46,0x47,0x48,0x49,0x4A,0x53,0x54,0x55,0x56,0x57,0x58,0x59,
            0x5A,0x63,0x64,0x65,0x66,0x67,0x68,0x69,0x6A,0x73,0x74,0x75,0x76,0x77,0x78,
            0x79,0x7A,(byte)0x83,(byte)0x84,(byte)0x85,(byte)0x86,(byte)0x87,(byte)0x88,
            (byte)0x89,(byte)0x8A,(byte)0x93,(byte)0x94,(byte)0x95,(byte)0x96,(byte)0x97,
            (byte)0x98,(byte)0x99,(byte)0x9A,(byte)0xA2,(byte)0xA3,(byte)0xA4,(byte)0xA5,
            (byte)0xA6,(byte)0xA7,(byte)0xA8,(byte)0xA9,(byte)0xAA,(byte)0xB2,(byte)0xB3,
            (byte)0xB4,(byte)0xB5,(byte)0xB6,(byte)0xB7,(byte)0xB8,(byte)0xB9,(byte)0xBA,
            (byte)0xC2,(byte)0xC3,(byte)0xC4,(byte)0xC5,(byte)0xC6,(byte)0xC7,(byte)0xC8,
            (byte)0xC9,(byte)0xCA,(byte)0xD2,(byte)0xD3,(byte)0xD4,(byte)0xD5,(byte)0xD6,
            (byte)0xD7,(byte)0xD8,(byte)0xD9,(byte)0xDA,(byte)0xE1,(byte)0xE2,(byte)0xE3,
            (byte)0xE4,(byte)0xE5,(byte)0xE6,(byte)0xE7,(byte)0xE8,(byte)0xE9,(byte)0xEA,
            (byte)0xF1,(byte)0xF2,(byte)0xF3,(byte)0xF4,(byte)0xF5,(byte)0xF6,(byte)0xF7,
            (byte)0xF8,(byte)0xF9,(byte)0xFA,(byte)0xFF,(byte)0xDA,0x00,0x08,0x01,0x01,
            0x00,0x00,0x3F,0x00,(byte)0xFB,(byte)0xD2,(byte)0xFF,(byte)0xD9
    };

    @BeforeEach
    void setupSecurityContextAndScanDir() throws IOException {
        // Set up a Spring Security context with a UserDetailsImpl principal.
        // The seed migration inserts user_account id=1 ("admin"), so we use id=1L here.
        var principal = new UserDetailsImpl(
                1L, "admin", "Admin", "admin@nohost.nodomain", "Disabled",
                Set.of(),
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Create a uniquely named temp directory under originalRootDirectory
        relativeScanDirName = "itc-dps-" + UUID.randomUUID().toString().substring(0, 8);
        testScanDir = Path.of(originalRootDirectory, relativeScanDirName);
        Files.createDirectories(testScanDir);
    }

    @AfterEach
    void cleanup() throws IOException {
        SecurityContextHolder.clearContext();
        // Remove test directory and its contents
        if (testScanDir != null && Files.exists(testScanDir)) {
            try (var stream = Files.walk(testScanDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                      .map(Path::toFile)
                      .forEach(java.io.File::delete);
            }
        }
    }

    @Test
    @DisplayName("scanOriginalDirectory with empty directory returns zero counts")
    void scanEmptyDirectory_returnsZeroCounts() {
        var req = new ScanRequest("/" + relativeScanDirName, null);

        var responses = fileScannerService.scanDirectories(req);

        assertThat(responses.getScanOriginalResponse()).isNotNull();
        assertThat(responses.getScanOriginalResponse().getScannedFilesCount()).isEqualTo(0L);
        assertThat(responses.getScanOriginalResponse().getNewFilesCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("scanOriginalDirectory processes a JPEG file - invokes full processOriginalFile flow")
    void scanDirectoryWithJpeg_processesFile() throws IOException {
        // Place a minimal JPEG in the test scan directory
        var jpegFile = testScanDir.resolve("test-photo.jpg");
        Files.write(jpegFile, MINIMAL_JPEG);

        var req = new ScanRequest("/" + relativeScanDirName, null);

        var responses = fileScannerService.scanDirectories(req);

        assertThat(responses.getScanOriginalResponse()).isNotNull();
        // At least 1 file was scanned
        assertThat(responses.getScanOriginalResponse().getScannedFilesCount()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("scanOriginalDirectory processes multiple files in the same directory")
    void scanDirectoryWithMultipleFiles_processesAll() throws IOException {
        Files.write(testScanDir.resolve("photo1.jpg"), MINIMAL_JPEG);
        Files.write(testScanDir.resolve("photo2.jpg"), MINIMAL_JPEG);

        var req = new ScanRequest("/" + relativeScanDirName, null);

        var responses = fileScannerService.scanDirectories(req);

        assertThat(responses.getScanOriginalResponse()).isNotNull();
        assertThat(responses.getScanOriginalResponse().getScannedFilesCount()).isGreaterThanOrEqualTo(2L);
    }

    @Test
    @DisplayName("scanOriginalDirectory rescanning same file does not duplicate it")
    void rescanningSameFile_doesNotDuplicate() throws IOException {
        Files.write(testScanDir.resolve("unique.jpg"), MINIMAL_JPEG);

        var req = new ScanRequest("/" + relativeScanDirName, null);

        fileScannerService.scanDirectories(req);
        var responses2 = fileScannerService.scanDirectories(req);

        // Second scan should see the file as already existing (0 new)
        assertThat(responses2.getScanOriginalResponse()).isNotNull();
        assertThat(responses2.getScanOriginalResponse().getNewFilesCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("scanOriginalDirectory handles nested subdirectory")
    void scanWithSubdirectory_processesLeafDirs() throws IOException {
        var subDir = testScanDir.resolve("sub");
        Files.createDirectories(subDir);
        Files.write(subDir.resolve("subphoto.jpg"), MINIMAL_JPEG);

        var req = new ScanRequest("/" + relativeScanDirName, null);

        var responses = fileScannerService.scanDirectories(req);

        assertThat(responses.getScanOriginalResponse()).isNotNull();
        assertThat(responses.getScanOriginalResponse().getScannedFilesCount()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("scanExportDirectory on empty directory returns zero counts")
    void scanEmptyExportDirectory_returnsZeroCounts() throws IOException {
        var exportDir = Path.of("/tmp", "itc-export-" + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectories(exportDir);

        try {
            var req = new ScanRequest(null, "/" + exportDir.getFileName());

            var responses = fileScannerService.scanDirectories(req);

            assertThat(responses.getScanExportResponse()).isNotNull();
            assertThat(responses.getScanExportResponse().getScannedFilesCount()).isEqualTo(0L);
        } finally {
            exportDir.toFile().delete();
        }
    }
}
