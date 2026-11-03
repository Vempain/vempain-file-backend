package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import fi.poltsi.vempain.admin.api.response.DataSetAclResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestAclResponse;
import fi.poltsi.vempain.file.feign.VempainAdminDataClient;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
 * <li>GET /api/data-publish/{identifier}/status – whether the admin backend holds the data set and its grantees</li>
 * </ul>
 */
class DataPublishControllerCTC extends AbstractControllerCTC {

    private static final String MUSIC_PATH = "/data-publish/music";
    private static final String GPS_PATH = "/data-publish/gps-timeseries";

    /** The admin backend is not reachable from the test; the status lookup goes through this stand-in of the Feign client */
    @MockitoBean
    private VempainAdminDataClient vempainAdminDataClient;

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

        @Test
        void acceptsTheOptionalPublishToSiteBody() throws Exception {
            // the body is read (no 400) and the data set check still answers 404 without music files
            doPost(MUSIC_PATH, "{\"publish_to_site\":true}").andExpect(status().isNotFound());
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

        @Test
        void returns404WhenTheGpsImagesWereNeverPublished() throws Exception {
            // An exported, GPS-tagged image that was never published to the admin backend is not part of a published GPS series
            jdbcTemplate.update("INSERT INTO gps_locations(latitude, latitude_ref, longitude, longitude_ref) VALUES (60.17, 'N', 24.94, 'E')");
            var gpsId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM gps_locations", Long.class);
            seedFileRow(9203L, "IMAGE", "image/jpeg", "unpublished.jpg", "/ctc-gps-unpublished");
            jdbcTemplate.update("INSERT INTO image_files(id, width, height, color_depth, dpi, group_label) VALUES (?, 16, 16, 24, 72, '')", 9203L);
            jdbcTemplate.update("UPDATE files SET gps_location_id = ?, original_datetime = NOW(), site_file_published = false WHERE id = ?", gpsId, 9203L);
            jdbcTemplate.update(
                    "INSERT INTO export_files(file_id, filename, file_path, mimetype, filesize, sha256sum, created) VALUES (?, ?, ?, ?, ?, ?, NOW())", 9203L,
                    "unpublished.jpg", "/ctc-gps-unpublished", "image/jpeg", 10L, "1".repeat(64));
            jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/ctc-gps-unpublished", "GPS Group", "Test");
            var groupId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);
            jdbcTemplate.update("INSERT INTO file_group_files(file_group_id, file_id) VALUES (?, ?)", groupId, 9203L);
            try {
                doPost(GPS_PATH, "{\"file_group_id\":" + groupId + ",\"time_series_name\":\"unpublished_series\"}").andExpect(status().isNotFound());
            } finally {
                deleteFileRow(9203L);
            }
        }

        @Test
        void acceptsTheOptionalPublishToSiteFlag() throws Exception {
            jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/gps/flag", "GPS Group", "Test");
            Long groupId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);
            // the flag is part of the contract (no 400); without GPS images the answer stays 404
            doPost(GPS_PATH, "{\"file_group_id\":" + groupId + ",\"time_series_name\":\"flag_series\",\"publish_to_site\":true}")
                .andExpect(status().isNotFound());
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
            // only an exported image that was published to the admin backend may feed the series
            jdbcTemplate.update("UPDATE files SET gps_location_id = ?, original_datetime = NOW(), site_file_published = true WHERE id = ?", gpsId, 9202L);
            jdbcTemplate.update(
                    "INSERT INTO export_files(file_id, filename, file_path, mimetype, filesize, sha256sum, created) VALUES (?, ?, ?, ?, ?, ?, NOW())", 9202L,
                    "geo.jpg", "/ctc-gps", "image/jpeg", 10L, "0".repeat(64));
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

    @Nested
    @DisplayName("GET /api/data-publish/{identifier}/status")
    class DataSetStatus {

        @Test
        void answersTheGranteesOfAnExistingDataSetInRequestShape() throws Exception {
            when(vempainAdminDataClient.getDataSetAcls("music_library")).thenReturn(ResponseEntity.ok(DataSetAclResponse.builder()
                .identifier("music_library")
                .aclId(42L)
                .acls(java.util.List.of(FileIngestAclResponse.builder()
                    .unitId(3L)
                    .readPrivilege(true)
                    .modifyPrivilege(true)
                    .build()))
                .build()));

            doGet("/data-publish/music_library/status").andExpect(status().isOk())
                .andExpect(jsonPath("$.identifier").value("music_library"))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.acls[0].unit_id").value(3))
                .andExpect(jsonPath("$.acls[0].user_id").isEmpty())
                .andExpect(jsonPath("$.acls[0].read_privilege").value(true))
                .andExpect(jsonPath("$.acls[0].modify_privilege").value(true))
                .andExpect(jsonPath("$.acls[0].delete_privilege").value(false));
        }

        @Test
        void aDataSetTheAdminBackendDoesNotHoldIsNotPublished() throws Exception {
            when(vempainAdminDataClient.getDataSetAcls(anyString())).thenThrow(feign.FeignException.errorStatus("status", feign.Response.builder()
                .status(404)
                .reason("not found")
                .request(feign.Request.create(feign.Request.HttpMethod.GET, "http://admin", java.util.Map.of(), null, new feign.RequestTemplate()))
                .headers(java.util.Map.of())
                .build()));

            doGet("/data-publish/gps_timeseries_trip/status").andExpect(status().isOk())
                .andExpect(jsonPath("$.identifier").value("gps_timeseries_trip"))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.acls").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/data-publish/paged")
    class PagedDataSets {

        @Test
        void proxiesThePageOfTheAdminBackend() throws Exception {
            var page = fi.poltsi.vempain.auth.api.response.PagedResponse.of(java.util.List.of(fi.poltsi.vempain.admin.api.response.DataSummaryResponse.builder()
                .id(5L)
                .aclId(42L)
                .identifier("music_library")
                .type("tabulated")
                .creator(1L)
                .build()), 0, 25, 1, 1, true, true);
            when(vempainAdminDataClient.getPagedDataSets(org.mockito.ArgumentMatchers.any())).thenReturn(ResponseEntity.ok(page));

            doPost("/data-publish/paged", "{\"page\":0,\"size\":25,\"sort_by\":\"identifier\",\"direction\":\"ASC\",\"modified_after_publish\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].identifier").value("music_library"))
                .andExpect(jsonPath("$.content[0].acl_id").value(42))
                .andExpect(jsonPath("$.content[0].creator").value(1))
                .andExpect(jsonPath("$.content[0].acls").doesNotExist())
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.last").value(true));

            // the request reaches the admin backend as sent
            var captor = org.mockito.ArgumentCaptor.forClass(fi.poltsi.vempain.admin.api.request.DataPagedRequest.class);
            org.mockito.Mockito.verify(vempainAdminDataClient)
                .getPagedDataSets(captor.capture());
            org.junit.jupiter.api.Assertions.assertEquals("identifier", captor.getValue()
                .getSortBy());
            org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, captor.getValue()
                .getModifiedAfterPublish());

            // the paging parameters are validated here, before the admin backend is asked
            doPost("/data-publish/paged", "{\"page\":0,\"size\":0}").andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("grantees in the create requests")
    class Grantees {

        @Test
        void theGranteeListIsPartOfTheContract() throws Exception {
            jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/gps/acl", "GPS Group", "Test");
            Long groupId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);
            // the list is read (no 400); without GPS images the answer stays 404
            doPost(GPS_PATH, "{\"file_group_id\":" + groupId + ",\"time_series_name\":\"acl_series\",\"acls\":[{\"user_id\":12,\"read_privilege\":true}]}")
                .andExpect(status().isNotFound());
            doPost(MUSIC_PATH, "{\"acls\":[{\"unit_id\":3,\"read_privilege\":true}]}").andExpect(status().isNotFound());
            // an entry naming neither a user nor a unit, or granting nothing, is rejected by the contract validation
            doPost(MUSIC_PATH, "{\"acls\":[{\"read_privilege\":true}]}").andExpect(status().isBadRequest());
            doPost(MUSIC_PATH, "{\"acls\":[{\"user_id\":12}]}").andExpect(status().isBadRequest());
        }
    }
}
