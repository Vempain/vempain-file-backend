package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.file.controller.AbstractControllerCTC;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@link MusicFileController}.
 *
 * <p>Music files require entries in all three tables in the JOINED inheritance hierarchy:
 * {@code files}, {@code audio_files}, and {@code music_files}. Because the {@link FileTypeControllersCTC}
 * parameterized test only supports a single INSERT statement per type, music is covered here.
 */
class MusicFileControllerCTC extends AbstractControllerCTC {

	private static final long TEST_ID = 9010L;

	// -----------------------------------------------------------------------
	// Test 1: paged endpoint returns 200 with valid structure
	// -----------------------------------------------------------------------

	@Test
	void findAll_returns200WithValidPageShape() throws Exception {
		doPost("/files/music/paged", "{\"page\":0,\"size\":10}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());
	}

	// -----------------------------------------------------------------------
	// Test 2: findById 404 for missing id
	// -----------------------------------------------------------------------

	@Test
	void findById_returns404_whenRecordDoesNotExist() throws Exception {
		doGet("/files/music/99999")
				.andExpect(status().isNotFound());
	}

	// -----------------------------------------------------------------------
	// Test 3: findById 200 for seeded record
	// -----------------------------------------------------------------------

	@Test
	void findById_returns200_whenRecordExists() throws Exception {
		seedMusicRow(TEST_ID);
		try {
			doGet("/files/music/" + TEST_ID)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(TEST_ID))
					.andExpect(jsonPath("$.file_type").value("MUSIC"))
					.andExpect(jsonPath("$.artist").value("Test Artist"));
		} finally {
			deleteFileRow(TEST_ID);
		}
	}

	// -----------------------------------------------------------------------
	// Test 4: delete 200 for seeded record
	// -----------------------------------------------------------------------

	@Test
	void delete_returns200_whenRecordExists() throws Exception {
		seedMusicRow(TEST_ID);
		try {
			doDelete("/files/music/" + TEST_ID)
					.andExpect(status().isOk());
		} finally {
			jdbcTemplate.update("DELETE FROM files WHERE id = ?", TEST_ID);
		}
	}

	// -----------------------------------------------------------------------
	// Test 5: delete 404 for missing id
	// -----------------------------------------------------------------------

	@Test
	void delete_returns404_whenRecordDoesNotExist() throws Exception {
		doDelete("/files/music/99999")
				.andExpect(status().isNotFound());
	}

	// -----------------------------------------------------------------------
	// Seed helper
	// -----------------------------------------------------------------------

	private void seedMusicRow(long id) {
		seedFileRow(id, "MUSIC", "audio/mpeg", "test-music.mp3", "/test/music");
		jdbcTemplate.update(
				"INSERT INTO audio_files (id, duration, bit_rate, sample_rate, codec, channels) VALUES (?, 240, 320000, 44100, 'MP3', 2)",
				id);
		jdbcTemplate.update(
				"INSERT INTO music_files (id, artist, album, track_name, track_number, genre) VALUES (?, 'Test Artist', 'Test Album', 'Test Track', 1, 'Rock')",
				id);
	}
}
