package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.tools.SampleFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link FileContentController}: {@code GET /api/files/{id}/content} streams the original file of a
 * readable file row and answers 403 or 404 otherwise.
 */
class FileContentControllerCTC extends AbstractControllerCTC {

	private void seedImage(long id, String mimetype, String filename, String directory, Long aclUser) {
		seedFileRowWithAcl(id, "IMAGE", mimetype, filename, directory, aclUser, true, true, true);
		jdbcTemplate.update("INSERT INTO image_files (id, width, height, color_depth, dpi, group_label) VALUES (?, 16, 16, 24, 72, '')", id);
	}

	@Test
	void getFileContent_streamsTheOriginalWithItsMimeType(@TempDir Path directory) throws Exception {
		var image = SampleFiles.jpeg(directory.resolve("content.jpg"));
		seedImage(9401L, "image/jpeg", "content.jpg", "/" + directory.getFileName(), 1L);
		try {
			doGet("/files/9401/content")
					.andExpect(status().isOk())
					.andExpect(content().contentType(MediaType.IMAGE_JPEG))
					.andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, image.length()))
					.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
					.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("content.jpg")));
		} finally {
			deleteFileRow(9401L);
		}
	}

	@Test
	void getFileContent_fallsBackToOctetStreamForBlankOrInvalidMimeTypes(@TempDir Path directory) throws Exception {
		SampleFiles.jpeg(directory.resolve("blank.jpg"));
		SampleFiles.jpeg(directory.resolve("odd.jpg"));
		seedImage(9402L, "", "blank.jpg", "/" + directory.getFileName(), 1L);
		seedImage(9403L, "not a mime type", "odd.jpg", "/" + directory.getFileName(), 1L);
		try {
			doGet("/files/9402/content")
					.andExpect(status().isOk())
					.andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
			doGet("/files/9403/content")
					.andExpect(status().isOk())
					.andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
		} finally {
			deleteFileRow(9402L);
			deleteFileRow(9403L);
		}
	}

	@Test
	void getFileContent_isDeniedWithoutTheReadPrivilege(@TempDir Path directory) throws Exception {
		SampleFiles.jpeg(directory.resolve("private.jpg"));
		seedImage(9404L, "image/jpeg", "private.jpg", "/" + directory.getFileName(), 2L);
		try {
			doGet("/files/9404/content")
					.andExpect(status().isForbidden());
		} finally {
			deleteFileRow(9404L);
		}
	}

	@Test
	void getFileContent_returns404ForUnknownFilesAndMissingContent(@TempDir Path directory) throws Exception {
		doGet("/files/99999/content")
				.andExpect(status().isNotFound());

		seedImage(9405L, "image/jpeg", "gone.jpg", "/" + directory.getFileName(), 1L);
		try {
			doGet("/files/9405/content")
					.andExpect(status().isNotFound());
		} finally {
			deleteFileRow(9405L);
		}
	}
}
