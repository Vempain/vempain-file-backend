package fi.poltsi.vempain.file.controller;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link FileScannerController}.
 *
 * <p>
 * Tests the REST endpoint declared in {@code FileScannerAPI}:
 * <ul>
 * <li>POST /api/scan-files – scan a directory for new files</li>
 * </ul>
 *
 * <p>
 * The scan root is configured as {@code /tmp} in test properties. The scan endpoint accepts the request with 202 and a task acknowledgement; it returns 400
 * when the request fails Bean Validation or the directory is invalid.
 */
class FileScannerControllerCTC extends AbstractControllerCTC {

    private static final String TEST_SCAN_DIR = "/ctc-scan";

    @BeforeEach
    void ensureTestScanDirectoryExists() throws Exception {
        Files.createDirectories(Path.of("/tmp", TEST_SCAN_DIR.substring(1)));
    }

    // -----------------------------------------------------------------------
    // POST /api/scan-files
    // -----------------------------------------------------------------------

    @Test
    void scanReturns202WithValidOriginalDirectory() throws Exception {
        doPost("/scan-files", "{\"original_directory\":\"%s\"}".formatted(TEST_SCAN_DIR)).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.task_id").exists())
            .andExpect(jsonPath("$.type").value("SCAN_DIRECTORIES"))
            .andExpect(jsonPath("$.title").value("Scan " + TEST_SCAN_DIR));
    }

    @Test
    void scanReturns202WithValidExportDirectory() throws Exception {
        doPost("/scan-files", "{\"export_directory\":\"%s\"}".formatted(TEST_SCAN_DIR)).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.task_id").exists());
    }

    @Test
    void scanReturns400WhenDirectoryDoesNotExist() throws Exception {
        doPost("/scan-files", "{\"original_directory\":\"/ctc-scan-does-not-exist\"}").andExpect(status().isBadRequest());
    }

    @Test
    void scanReturns400WhenBothDirectoriesAreNull() throws Exception {
        // @AssertTrue on isDirectoryValid() fails when both fields are null → 400
        doPost("/scan-files", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void scanReturns400WhenDirectoryPathDoesNotMatchPattern() throws Exception {
        // Pattern requires path to start with '/' and contain only alphanumerics / dashes / underscores
        doPost("/scan-files", "{\"original_directory\":\"no-leading-slash\"}").andExpect(status().isBadRequest());
    }
}
