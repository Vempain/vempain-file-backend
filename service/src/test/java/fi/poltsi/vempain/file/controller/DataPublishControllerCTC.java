package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link DataPublishController}.
 *
 * <p>Validates REST endpoints declared in {@code DataPublishAPI}:
 * <ul>
 *   <li>POST /api/data-publish/music        – publish music CSV dataset (returns 404 when empty DB)</li>
 *   <li>POST /api/data-publish/gps-timeseries – publish GPS time-series dataset</li>
 * </ul>
 */
class DataPublishControllerCTC extends AbstractControllerCTC {

    private static final String MUSIC_PATH = "/api/data-publish/music";
    private static final String GPS_PATH   = "/api/data-publish/gps-timeseries";

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
        void returns404_whenNoMusicFilesExist() throws Exception {
            // No music files in the database → DataService throws 404
            doPost(MUSIC_PATH, "")
                    .andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------
    // POST /api/data-publish/gps-timeseries
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("POST /api/data-publish/gps-timeseries")
    class PublishGpsTimeSeries {

        @Test
        void returns404_whenNoGpsImagesInFileGroup() throws Exception {
            // Insert a file group but no GPS-tagged images
            jdbcTemplate.update(
                    "INSERT INTO file_group (path, group_name, description) VALUES (?, ?, ?)",
                    "/gps/test", "GPS Group", "Test");
            Long groupId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);

            doPost(GPS_PATH,
                   "{\"file_group_id\":" + groupId + ",\"time_series_name\":\"test_series\"}")
                    .andExpect(status().isNotFound());
        }

        @Test
        void returns400_whenRequestBodyMissingFileGroupId() throws Exception {
            // file_group_id is required — missing → validation error
            doPost(GPS_PATH, "{\"time_series_name\":\"test\"}")
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returns400_whenRequestBodyIsEmpty() throws Exception {
            doPost(GPS_PATH, "{}")
                    .andExpect(status().isBadRequest());
        }

    }
}
