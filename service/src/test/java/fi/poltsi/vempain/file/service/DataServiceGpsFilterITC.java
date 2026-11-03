package fi.poltsi.vempain.file.service;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import feign.FeignException;
import fi.poltsi.vempain.admin.api.request.DataRequest;
import fi.poltsi.vempain.admin.api.response.DataResponse;
import fi.poltsi.vempain.file.feign.VempainAdminDataClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two levels of filtering of a published GPS time series, against the real schema: the data set carries the coordinates of the images that were actually
 * published (an export file exists and the file was published to the admin backend), and never a coordinate that lies inside a location guard. The admin
 * backend only stores what it receives, so the filtering must be complete here.
 */
@SpringBootTest(properties = {
    "vempain.app.frontend-url=http://localhost:3000",
    "vempain.original-root-directory=/tmp",
    "vempain.export-root-directory=/tmp",
    "vempain.generate-missing-thumbnails.batch-size=10",
    "vempain.generate-missing-thumbnails.thumb-image-quality=0.5",
    "vempain.generate-missing-thumbnails.thumb-image-size=100",
    "vempain.tasks.poll-interval-ms=3600000",
    "vempain.tasks.heartbeat-interval-ms=3600000" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DataServiceGpsFilterITC {

    private static final String DIRECTORY = "/itc-gps-filter";
    private static final long PUBLISHED_OUTSIDE = 9301L;
    private static final long NO_EXPORT = 9302L;
    private static final long PUBLISHED_INSIDE_GUARD = 9303L;
    private static final long EXPORTED_NOT_PUBLISHED = 9304L;
    private static final List<Long> FILE_IDS = List.of(PUBLISHED_OUTSIDE, NO_EXPORT, PUBLISHED_INSIDE_GUARD, EXPORTED_NOT_PUBLISHED);

    @Autowired
    private DataService dataService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private VempainAdminDataClient vempainAdminDataClient;

    private long groupId;
    private long guardId;

    @BeforeEach
    void seed() {
        // A square guard around Helsinki centre; "inside" lies within it, "outside" clearly does not
        jdbcTemplate.update("INSERT INTO location_guard(guard_type, primary_latitude, primary_longitude, secondary_latitude, secondary_longitude) "
                + "VALUES ('SQUARE', 60.16, 24.93, 60.18, 24.95)");
        guardId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM location_guard", Long.class);

        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", DIRECTORY, "GPS filter group", "ITC");
        groupId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);

        seedImage(PUBLISHED_OUTSIDE, "published-outside.jpg", 60.30, 24.70, true, true);
        seedImage(NO_EXPORT, "original-only.jpg", 60.31, 24.71, false, true);
        seedImage(PUBLISHED_INSIDE_GUARD, "published-inside-guard.jpg", 60.17, 24.94, true, true);
        seedImage(EXPORTED_NOT_PUBLISHED, "exported-not-published.jpg", 60.32, 24.72, true, false);

        when(vempainAdminDataClient.getDataSetByIdentifier(any())).thenThrow(mock(FeignException.NotFound.class));
        var created = new DataResponse();
        created.setIdentifier("itc_gps");
        when(vempainAdminDataClient.createDataSet(any(DataRequest.class))).thenReturn(ResponseEntity.ok(created));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM file_group WHERE id = ?", groupId);
        for (var id : FILE_IDS) {
            jdbcTemplate.update("DELETE FROM files WHERE id = ?", id);
            jdbcTemplate.update("DELETE FROM acl WHERE acl_id = ?", id);
        }
        jdbcTemplate.update("DELETE FROM gps_locations WHERE id IN (SELECT gps_location_id FROM files WHERE id IN (9301, 9302, 9303, 9304))");
        jdbcTemplate.update("DELETE FROM location_guard WHERE id = ?", guardId);
    }

    @Test
    void theFileGroupSeriesCarriesOnlyThePublishedExportedImagesOutsideTheGuards() {
        dataService.publishGpsTimeSeriesNow(groupId, "itc_gps", null, false);

        var csv = publishedCsv();
        assertThat(csv).contains("published-outside.jpg");
        assertThat(csv).doesNotContain("original-only.jpg");
        assertThat(csv).doesNotContain("published-inside-guard.jpg");
        assertThat(csv).doesNotContain("exported-not-published.jpg");
        assertThat(csv.lines()
            .count()).as("header plus one point")
            .isEqualTo(2);
    }

    @Test
    void theDirectorySeriesAppliesTheSameFilters() {
        dataService.generateAndPublishGpsTimeSeries(DIRECTORY);

        var csv = publishedCsv();
        assertThat(csv).contains("published-outside.jpg");
        assertThat(csv).doesNotContain("original-only.jpg", "published-inside-guard.jpg", "exported-not-published.jpg");
    }

    @Test
    void aGroupWhoseOnlyPublishableImageLiesInAGuardHasNothingToPublish() {
        // Take the publishable point away: what remains is an original, a guarded point and an unpublished export
        jdbcTemplate.update("DELETE FROM file_group_files WHERE file_group_id = ? AND file_id = ?", groupId, PUBLISHED_OUTSIDE);

        assertThrows(ResponseStatusException.class, () -> dataService.publishGpsTimeSeriesNow(groupId, "itc_gps", null, false));
        verify(vempainAdminDataClient, org.mockito.Mockito.never()).createDataSet(any(DataRequest.class));
    }

    private String publishedCsv() {
        var captor = ArgumentCaptor.forClass(DataRequest.class);
        verify(vempainAdminDataClient).createDataSet(captor.capture());
        return captor.getValue()
            .getCsvData();
    }

    /** An image of the group at the given coordinate, with an export file and the published flag as requested */
    private void seedImage(long id, String filename, double latitude, double longitude, boolean exported, boolean published) {
        jdbcTemplate.update("INSERT INTO gps_locations(latitude, latitude_ref, longitude, longitude_ref) VALUES (?, 'N', ?, 'E')", latitude, longitude);
        var gpsId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM gps_locations", Long.class);
        jdbcTemplate.update(
                "INSERT INTO acl(acl_id, user_id, read_privilege, create_privilege, modify_privilege, delete_privilege) VALUES (?, 1, true, true, true, true)",
                id);
        jdbcTemplate.update("""
                INSERT INTO files (id, acl_id, external_file_id, filename, file_path, mimetype, filesize, sha256sum, file_type, creator, created, locked,
                                   metadata_raw, gps_location_id, original_datetime, site_file_published)
                OVERRIDING SYSTEM VALUE
                VALUES (?, ?, ?, ?, ?, 'image/jpeg', 1024, ?, 'IMAGE', 1, NOW(), false, '{}', ?, NOW(), ?)
                """, id, id, "itc-gps-" + id, filename, DIRECTORY, String.valueOf(id)
            .repeat(16), gpsId, published);
        jdbcTemplate.update("INSERT INTO image_files(id, width, height, color_depth, dpi, group_label) VALUES (?, 16, 16, 24, 72, '')", id);
        jdbcTemplate.update("INSERT INTO file_group_files(file_group_id, file_id) VALUES (?, ?)", groupId, id);
        if (exported) {
            jdbcTemplate.update(
                    "INSERT INTO export_files(file_id, filename, file_path, mimetype, filesize, sha256sum, created) "
                            + "VALUES (?, ?, ?, 'image/jpeg', 10, ?, NOW())",
                    id, filename, DIRECTORY, String.valueOf(id)
                        .repeat(16));
        }
    }
}
