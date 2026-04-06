package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.file.controller.AbstractControllerCTC;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) covering all 13 typed-file controllers.
 *
 * <p>Each file type exposes three endpoints via its API interface:
 * <ul>
 *   <li>POST /api/files/{type}/paged  – paged list</li>
 *   <li>GET  /api/files/{type}/{id}   – single record</li>
 *   <li>DELETE /api/files/{type}/{id} – remove</li>
 * </ul>
 *
 * <p>All tests are parameterized via {@link #fileTypeArgs()} so that every
 * type is exercised with the same four test scenarios:
 * <ol>
 *   <li>findAll returns 200 with valid page structure</li>
 *   <li>findById returns 404 for a missing id</li>
 *   <li>findById returns 200 for a seeded record</li>
 *   <li>delete returns 200 for a seeded record</li>
 * </ol>
 *
 * <p>Test records are seeded with id=9001 and cleaned up after each test.
 * The Flyway-seeded document file (id=1) is not touched.
 */
class FileTypeControllersCTC extends AbstractControllerCTC {

	// -----------------------------------------------------------------------
	// Argument source — one entry per file type
	// -----------------------------------------------------------------------

	/**
	 * Arguments: (urlSegment, fileType, mimeType, filename, filePath, typeInsertSql)
	 */
	static Stream<Arguments> fileTypeArgs() {
		return Stream.of(
				Arguments.of(
						"archive", "ARCHIVE", "application/zip",
						"test-file.zip", "/test/archive",
						"INSERT INTO archive_files (id, compression_method, uncompressed_size, content_count, is_encrypted) VALUES (9001, 'zip', 2048, 5, false)"
				),
				Arguments.of(
						"audio", "AUDIO", "audio/mpeg",
						"test-file.mp3", "/test/audio",
						"INSERT INTO audio_files (id, duration, bit_rate, sample_rate, codec, channels) VALUES (9001, 180, 192000, 44100, 'MP3', 2)"
				),
				Arguments.of(
						"binary", "BINARY", "application/octet-stream",
						"test-file.bin", "/test/binary",
						"INSERT INTO binary_files (id, software_name, software_major_version) VALUES (9001, 'TestApp', 1)"
				),
				Arguments.of(
						"data", "DATA", "text/csv",
						"test-file.csv", "/test/data",
						"INSERT INTO data_files (id, data_structure) VALUES (9001, 'CSV')"
				),
				Arguments.of(
						"document", "DOCUMENT", "application/pdf",
						"test-file.pdf", "/test/document",
						"INSERT INTO document_files (id, page_count, format) VALUES (9001, 10, 'PDF')"
				),
				Arguments.of(
						"executable", "EXECUTABLE", "application/x-executable",
						"test-file.sh", "/test/executable",
						"INSERT INTO executable_files (id, is_script) VALUES (9001, true)"
				),
				Arguments.of(
						"font", "FONT", "font/ttf",
						"test-file.ttf", "/test/font",
						"INSERT INTO font_files (id, font_family, weight, style) VALUES (9001, 'TestFont', 'regular', 'normal')"
				),
				Arguments.of(
						"icon", "ICON", "image/x-icon",
						"test-file.ico", "/test/icon",
						"INSERT INTO icon_files (id, width, height, is_scalable) VALUES (9001, 64, 64, false)"
				),
				Arguments.of(
						"image", "IMAGE", "image/jpeg",
						"test-file.jpg", "/test/image",
						"INSERT INTO image_files (id, width, height, color_depth, dpi, group_label) VALUES (9001, 1920, 1080, 24, 72, 'test-group')"
				),
				Arguments.of(
						"interactive", "INTERACTIVE", "text/html",
						"test-file.html", "/test/interactive",
						"INSERT INTO interactive_files (id, technology) VALUES (9001, 'HTML5')"
				),
				Arguments.of(
						"thumb", "THUMB", "image/jpeg",
						"test-thumb.jpg", "/test/thumb",
						"INSERT INTO thumb_files (id, target_file_id, relation_type) VALUES (9001, NULL, 'thumbnail')"
				),
				Arguments.of(
						"vector", "VECTOR", "image/svg+xml",
						"test-file.svg", "/test/vector",
						"INSERT INTO vector_files (id, width, height, layers_count) VALUES (9001, 800, 600, 3)"
				),
				Arguments.of(
						"video", "VIDEO", "video/mp4",
						"test-file.mp4", "/test/video",
						"INSERT INTO video_files (id, width, height, frame_rate, duration, codec) VALUES (9001, 1920, 1080, 25.0, 120, 'H264')"
				)
		);
	}

	// -----------------------------------------------------------------------
	// Test 1: findAll returns 200 with valid paged response shape
	// -----------------------------------------------------------------------

	@ParameterizedTest(name = "[{index}] findAll({0})")
	@MethodSource("fileTypeArgs")
	void findAll_returns200WithValidPageShape(
			String urlSegment, String fileType, String mimeType,
			String filename, String filePath, String typeInsertSql) throws Exception {

		doPost("/files/" + urlSegment + "/paged", "{\"page\":0,\"size\":10}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());
	}

	// -----------------------------------------------------------------------
	// Test 2: findById returns 404 for a missing id
	// -----------------------------------------------------------------------

	@ParameterizedTest(name = "[{index}] findById_404({0})")
	@MethodSource("fileTypeArgs")
	void findById_returns404_whenRecordDoesNotExist(
			String urlSegment, String fileType, String mimeType,
			String filename, String filePath, String typeInsertSql) throws Exception {

		doGet("/files/" + urlSegment + "/99999")
				.andExpect(status().isNotFound());
	}

	// -----------------------------------------------------------------------
	// Test 3: findById returns 200 for a seeded record
	// -----------------------------------------------------------------------

	@ParameterizedTest(name = "[{index}] findById_200({0})")
	@MethodSource("fileTypeArgs")
	void findById_returns200_whenRecordExists(
			String urlSegment, String fileType, String mimeType,
			String filename, String filePath, String typeInsertSql) throws Exception {

		seedFileRow(9001L, fileType, mimeType, filename, filePath);
		jdbcTemplate.update(typeInsertSql);

		try {
			doGet("/files/" + urlSegment + "/9001")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(9001))
					.andExpect(jsonPath("$.file_type").value(fileType));
		} finally {
			deleteFileRow(9001L);
		}
	}

	// -----------------------------------------------------------------------
	// Test 4: delete returns 200 for a seeded record
	// -----------------------------------------------------------------------

	@ParameterizedTest(name = "[{index}] delete_200({0})")
	@MethodSource("fileTypeArgs")
	void delete_returns200_whenRecordExists(
			String urlSegment, String fileType, String mimeType,
			String filename, String filePath, String typeInsertSql) throws Exception {

		seedFileRow(9001L, fileType, mimeType, filename, filePath);
		jdbcTemplate.update(typeInsertSql);

		try {
			doDelete("/files/" + urlSegment + "/9001")
					.andExpect(status().isOk());
		} finally {
			// cleanup in case delete did not succeed
			jdbcTemplate.update("DELETE FROM files WHERE id = 9001");
		}
	}

	// -----------------------------------------------------------------------
	// Test 5: delete returns 404 for a missing id
	// -----------------------------------------------------------------------

	@ParameterizedTest(name = "[{index}] delete_404({0})")
	@MethodSource("fileTypeArgs")
	void delete_returns404_whenRecordDoesNotExist(
			String urlSegment, String fileType, String mimeType,
			String filename, String filePath, String typeInsertSql) throws Exception {

		doDelete("/files/" + urlSegment + "/99999")
				.andExpect(status().isNotFound());
	}
}

