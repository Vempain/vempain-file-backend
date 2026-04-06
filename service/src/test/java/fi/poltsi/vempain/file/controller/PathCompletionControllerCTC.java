package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link PathCompletionController}.
 *
 * <p>Tests the REST endpoint declared in {@code PathCompletionAPI}:
 * <ul>
 *   <li>POST /api/path-completion – complete a directory path</li>
 * </ul>
 *
 * <p>The original and export root directories are set to {@code /tmp} in
 * test properties, so path completions operate on the host's /tmp directory.
 */
class PathCompletionControllerCTC extends AbstractControllerCTC {

	// -----------------------------------------------------------------------
	// POST /api/path-completion
	// -----------------------------------------------------------------------

	@Test
	void completePath_returns200_withValidOriginalRequest() throws Exception {
		// "/" is valid per the pattern and lists sub-directories of /tmp
		doPost("/path-completion",
		       "{\"path\":\"/\",\"type\":\"ORIGINAL\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completions").isArray());
	}

	@Test
	void completePath_returns200_withValidExportedRequest() throws Exception {
		doPost("/path-completion",
		       "{\"path\":\"/\",\"type\":\"EXPORTED\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completions").isArray());
	}

	@Test
	void completePath_returns400_whenPathDoesNotStartWithSlash() throws Exception {
		// Pattern requires path to start with '/' — "no-slash" violates it
		doPost("/path-completion",
		       "{\"path\":\"no-slash\",\"type\":\"ORIGINAL\"}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void completePath_returns400_whenPathIsBlank() throws Exception {
		// @NotBlank on path field
		doPost("/path-completion",
		       "{\"path\":\"\",\"type\":\"ORIGINAL\"}")
				.andExpect(status().isBadRequest());
	}
}

