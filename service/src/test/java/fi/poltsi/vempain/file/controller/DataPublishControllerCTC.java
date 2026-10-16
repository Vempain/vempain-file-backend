package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link DataPublishController}.
 *
 * <p>
 * Validates REST endpoints declared in {@code DataPublishAPI}:
 * <ul>
 * <li>POST /api/data-publish/music – publish music CSV dataset (returns 404 when empty DB)</li>
 * <li>POST /api/data-publish/gps-timeseries – publish GPS time-series dataset</li>
 * </ul>
 */
class DataPublishControllerCTC extends AbstractControllerCTC {

    private static final String MUSIC_PATH = "/data-publish/music";
    private static final String GPS_PATH = "/data-publish/gps-timeseries";

    @BeforeEach
    void cleanData() {
        // Ensure a clean slate (no music files, no GPS images)
        jdbcTemplate.execute("TRUNCATE TABLE file_group RESTART IDENTITY CASCADE");
    }

    // ------------------------------------------------------------------
    // POST /api/data-publish/music
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("POST /api/data-publish/music")
    class PublishMusicDataset {

        @Test
        void returns404WhenNoMusicFilesExist() throws Exception {
            // No music files in the database → DataService throws 404
            doPost(MUSIC_PATH, "").andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------
    // POST /api/data-publish/gps-timeseries
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("POST /api/data-publish/gps-timeseries")
    class PublishGpsTimeSeries {

        @Test
        void returns404WhenNoGpsImagesInFileGroup() throws Exception {
            // Insert a file group but no GPS-tagged images
            jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/gps/test", "GPS Group", "Test");
            Long groupId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);

            doPost(GPS_PATH, "{\"file_group_id\":" + groupId + ",\"time_series_name\":\"test_series\"}").andExpect(status().isNotFound());
        }

        @Test
        void returns400WhenRequestBodyMissingFileGroupId() throws Exception {
            // file_group_id is required — missing → validation error
            doPost(GPS_PATH, "{\"time_series_name\":\"test\"}").andExpect(status().isBadRequest());
        }

        @Test
        void returns400WhenRequestBodyIsEmpty() throws Exception {
            doPost(GPS_PATH, "{}").andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("accepted publications")
    class AcceptedPublications {

        @Test
        void publishMusicReturns202WhenMusicFilesExist() throws Exception {
            seedFileRow(9201L, "MUSIC", "audio/mpeg", "song.mp3", "/ctc-music");
            jdbcTemplate.update("INSERT INTO audio_files(id, duration, bit_rate, sample_rate, codec, channels) VALUES (?, 10, 128, 44100, 'mp3', 2)", 9201L);
            jdbcTemplate.update("INSERT INTO music_files(id, artist, album_artist, album, year, track_name, track_number, track_total, genre) "
                    + "VALUES (?, 'Artist', 'Artist', 'Album', 2024, 'Song', 1, 1, 'Rock')", 9201L);
            try {
                doPost(MUSIC_PATH, "").andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.task_id", notNullValue()))
                    .andExpect(jsonPath("$.type").value("PUBLISH_MUSIC_DATA"));
            } finally {
                deleteFileRow(9201L);
            }
        }

        @Test
        void publishGpsReturns202WhenTheGroupHasGpsImages() throws Exception {
            jdbcTemplate.update("INSERT INTO gps_locations(latitude, latitude_ref, longitude, longitude_ref) VALUES (60.17, 'N', 24.94, 'E')");
            var gpsId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM gps_locations", Long.class);
            seedFileRow(9202L, "IMAGE", "image/jpeg", "geo.jpg", "/ctc-gps");
            jdbcTemplate.update("INSERT INTO image_files(id, width, height, color_depth, dpi, group_label) VALUES (?, 16, 16, 24, 72, '')", 9202L);
            jdbcTemplate.update("UPDATE files SET gps_location_id = ?, original_datetime = NOW() WHERE id = ?", gpsId, 9202L);
            jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/ctc-gps", "GPS Group", "Test");
            var groupId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);
            jdbcTemplate.update("INSERT INTO file_group_files(file_group_id, file_id) VALUES (?, ?)", groupId, 9202L);
            try {
                doPost(GPS_PATH, "{\"file_group_id\":" + groupId + ",\"time_series_name\":\"CTC Series\"}").andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.task_id", notNullValue()))
                    .andExpect(jsonPath("$.type").value("PUBLISH_GPS_TIME_SERIES"));
            } finally {
                deleteFileRow(9202L);
                jdbcTemplate.update("DELETE FROM gps_locations WHERE id = ?", gpsId);
            }
        }
    }
}
