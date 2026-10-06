package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link FileScannerController}.
 *
 * <p>Tests the REST endpoint declared in {@code FileScannerAPI}:
 * <ul>
 *   <li>POST /api/scan-files – scan a directory for new files</li>
 * </ul>
 *
 * <p>The scan root is configured as {@code /tmp} in test properties.
 * The scan endpoint accepts the request with 202 and a task acknowledgement; it
 * returns 400 when the request fails Bean Validation or the directory is invalid.
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
	void scan_returns202_withValidOriginalDirectory() throws Exception {
		doPost("/scan-files", "{\"original_directory\":\"%s\"}".formatted(TEST_SCAN_DIR))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.task_id").exists())
				.andExpect(jsonPath("$.type").value("SCAN_DIRECTORIES"))
				.andExpect(jsonPath("$.title").value("Scan " + TEST_SCAN_DIR));
	}

	@Test
	void scan_returns202_withValidExportDirectory() throws Exception {
		doPost("/scan-files", "{\"export_directory\":\"%s\"}".formatted(TEST_SCAN_DIR))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.task_id").exists());
	}

	@Test
	void scan_returns400_whenDirectoryDoesNotExist() throws Exception {
		doPost("/scan-files", "{\"original_directory\":\"/ctc-scan-does-not-exist\"}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void scan_returns400_whenBothDirectoriesAreNull() throws Exception {
		// @AssertTrue on isDirectoryValid() fails when both fields are null → 400
		doPost("/scan-files", "{}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void scan_returns400_whenDirectoryPathDoesNotMatchPattern() throws Exception {
		// Pattern requires path to start with '/' and contain only alphanumerics / dashes / underscores
		doPost("/scan-files", "{\"original_directory\":\"no-leading-slash\"}")
				.andExpect(status().isBadRequest());
	}
}

