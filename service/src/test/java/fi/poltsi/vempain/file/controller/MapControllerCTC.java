package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The clustered map of the GPS-tagged files against the real schema: four files around Helsinki and one in Tampere, one of them unreadable for the caller,
 * clustered coarsely at a low zoom and as single points at street level, narrowed by the filters, and listed per cluster with their thumbnails.
 */
class MapControllerCTC extends AbstractControllerCTC {

    private static final long HELSINKI_A = 9301L;
    private static final long HELSINKI_B = 9302L;
    private static final long HELSINKI_VIDEO = 9303L;
    private static final long HELSINKI_DENIED = 9304L;
    private static final long TAMPERE = 9305L;
    private static final long THUMB_OF_A = 9306L;

    private long tagId;

    @BeforeEach
    void seed() {
        // Helsinki centre: three readable images/videos plus one image the caller may not read; Tampere: one image 160 km north
        seedGpsFile(new GpsFile(HELSINKI_A, "IMAGE", "image/jpeg", "helsinki_a.jpg", 60.17123, 24.94156, "2026-06-01 12:00:00", true));
        seedGpsFile(new GpsFile(HELSINKI_B, "IMAGE", "image/jpeg", "helsinki_b.jpg", 60.17456, 24.94890, "2026-07-01 12:00:00", true));
        seedGpsFile(new GpsFile(HELSINKI_VIDEO, "VIDEO", "video/mp4", "helsinki.mp4", 60.16890, 24.93210, "2026-08-01 12:00:00", true));
        seedGpsFile(new GpsFile(HELSINKI_DENIED, "IMAGE", "image/jpeg", "secret.jpg", 60.17000, 24.94000, "2026-06-15 12:00:00", false));
        seedGpsFile(new GpsFile(TAMPERE, "IMAGE", "image/jpeg", "tampere.jpg", 61.49800, 23.76100, "2025-12-24 12:00:00", true));
        // a thumbnail of the first image, and a tag on the second
        seedFileRow(THUMB_OF_A, "THUMB", "image/jpeg", "helsinki_a_thumb.jpg", "/ctc-map/thumbs");
        jdbcTemplate.update("INSERT INTO thumb_files(id, target_file_id, relation_type) VALUES (?, ?, 'thumbnail')", THUMB_OF_A, HELSINKI_A);
        jdbcTemplate.update("INSERT INTO tags(tag_name) VALUES ('ctc-summer')");
        tagId = jdbcTemplate.queryForObject("SELECT id FROM tags WHERE tag_name = 'ctc-summer'", Long.class);
        jdbcTemplate.update("INSERT INTO file_tags(file_id, tag_id) VALUES (?, ?)", HELSINKI_B, tagId);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM file_tags WHERE tag_id = ?", tagId);
        jdbcTemplate.update("DELETE FROM tags WHERE id = ?", tagId);
        for (var id : new long[]{ THUMB_OF_A, HELSINKI_A, HELSINKI_B, HELSINKI_VIDEO, HELSINKI_DENIED, TAMPERE }) {
            deleteFileRow(id);
        }
        jdbcTemplate.update("DELETE FROM gps_locations WHERE id IN (SELECT id FROM gps_locations WHERE country = 'ctc-map')");
    }

    @Test
    void aLowZoomMergesTheReadableFilesOfACityIntoOneClusterAndStreetLevelShowsThemAsPoints() throws Exception {
        // zoom 3: one decimal, Helsinki (60.2, 24.9) and Tampere (61.5, 23.8) are separate cells; the unreadable file is never counted
        doPost("/map/clusters", "{\"south\": 59, \"west\": 20, \"north\": 63, \"east\": 30, \"zoom\": 3}").andExpect(status().isOk())
            .andExpect(jsonPath("$.precision").value(1))
            .andExpect(jsonPath("$.clustered").value(true))
            .andExpect(jsonPath("$.truncated").value(false))
            .andExpect(jsonPath("$.total_points").value(4))
            .andExpect(jsonPath("$.clusters", hasSize(2)))
            .andExpect(jsonPath("$.clusters[0].count").value(3))
            .andExpect(jsonPath("$.clusters[0].cell_latitude").value(60.2))
            .andExpect(jsonPath("$.clusters[0].cell_longitude").value(24.9))
            .andExpect(jsonPath("$.clusters[0].file_id").isEmpty())
            .andExpect(jsonPath("$.clusters[1].count").value(1))
            .andExpect(jsonPath("$.clusters[1].file_id").value(TAMPERE))
            .andExpect(jsonPath("$.clusters[1].filename").value("tampere.jpg"));

        // zoom 18: five decimals, every readable file inside the viewport is its own marker; Tampere is outside the viewport
        doPost("/map/clusters", "{\"south\": 60.1, \"west\": 24.8, \"north\": 60.3, \"east\": 25.1, \"zoom\": 18}").andExpect(status().isOk())
            .andExpect(jsonPath("$.precision").value(5))
            .andExpect(jsonPath("$.clustered").value(false))
            .andExpect(jsonPath("$.total_points").value(3))
            .andExpect(jsonPath("$.clusters", hasSize(3)))
            .andExpect(jsonPath("$.clusters[*].file_id").value(org.hamcrest.Matchers.containsInAnyOrder(9301, 9302, 9303)));
    }

    @Test
    void theFiltersNarrowTheClusters() throws Exception {
        var viewport = "\"south\": 59, \"west\": 20, \"north\": 63, \"east\": 30, \"zoom\": 3";
        doPost("/map/clusters", "{" + viewport + ", \"file_type\": \"video\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.total_points").value(1))
            .andExpect(jsonPath("$.clusters[0].file_id").value(HELSINKI_VIDEO));
        doPost("/map/clusters", "{" + viewport + ", \"tags\": [\"CTC-Summer\"]}").andExpect(status().isOk())
            .andExpect(jsonPath("$.total_points").value(1))
            .andExpect(jsonPath("$.clusters[0].file_id").value(HELSINKI_B));
        doPost("/map/clusters", "{" + viewport + ", \"search\": \"HELSINKI_\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.total_points").value(2));
        doPost("/map/clusters", "{" + viewport + ", \"from\": \"2026-06-20T00:00:00Z\", \"to\": \"2026-07-31T00:00:00Z\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.total_points").value(1))
            .andExpect(jsonPath("$.clusters[0].file_id").value(HELSINKI_B));
        // the LIKE wildcards of the search are literal
        doPost("/map/clusters", "{" + viewport + ", \"search\": \"%\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.total_points").value(0));
    }

    @Test
    void theFilesOfAClusterAreListedNewestFirstWithTheirThumbnailsAndTheRequestsAreValidated() throws Exception {
        doPost("/map/files", "{\"cell_latitude\": 60.2, \"cell_longitude\": 24.9, \"precision\": 1}").andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].id").value(HELSINKI_VIDEO))
            .andExpect(jsonPath("$[0].file_type").value("VIDEO"))
            .andExpect(jsonPath("$[0].thumbnail_id").isEmpty())
            .andExpect(jsonPath("$[1].id").value(HELSINKI_B))
            .andExpect(jsonPath("$[2].id").value(HELSINKI_A))
            .andExpect(jsonPath("$[2].filename").value("helsinki_a.jpg"))
            .andExpect(jsonPath("$[2].file_path").value("/ctc-map"))
            .andExpect(jsonPath("$[2].latitude").value(60.17123))
            .andExpect(jsonPath("$[2].longitude").value(24.94156))
            .andExpect(jsonPath("$[2].thumbnail_id").value(THUMB_OF_A))
            .andExpect(jsonPath("$[2].original_datetime").isNotEmpty());
        // the cell given with more decimals than the precision is rounded to the cell; the limit cuts the list
        doPost("/map/files", "{\"cell_latitude\": 60.17123, \"cell_longitude\": 24.94156, \"precision\": 1, \"limit\": 2, \"file_type\": \"IMAGE\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(HELSINKI_B));
        // an empty cell
        doPost("/map/files", "{\"cell_latitude\": 0, \"cell_longitude\": 0, \"precision\": 1}").andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
        // validation: missing bounds, a zoom out of range, a precision beyond the stored decimals
        doPost("/map/clusters", "{\"zoom\": 3}").andExpect(status().isBadRequest());
        doPost("/map/clusters", "{\"south\": 59, \"west\": 20, \"north\": 63, \"east\": 30, \"zoom\": 40}").andExpect(status().isBadRequest());
        doPost("/map/files", "{\"cell_latitude\": 60.2, \"cell_longitude\": 24.9, \"precision\": 9}").andExpect(status().isBadRequest());
    }

    /** A GPS-tagged file to seed: signed coordinates, the time it was taken and whether the calling administrator may read it */
    private record GpsFile(long id, String fileType, String mimeType, String filename, double latitude, double longitude, String takenAt, boolean readable) {}

    private void seedGpsFile(GpsFile file) {
        jdbcTemplate.update("INSERT INTO gps_locations(latitude, latitude_ref, longitude, longitude_ref, country) VALUES (?, ?, ?, ?, 'ctc-map')",
                Math.abs(file.latitude()), file.latitude() < 0 ? "S" : "N", Math.abs(file.longitude()), file.longitude() < 0 ? "W" : "E");
        var gpsId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM gps_locations", Long.class);
        if (file.readable()) {
            seedFileRow(file.id(), file.fileType(), file.mimeType(), file.filename(), "/ctc-map");
        } else {
            // an ACL that grants nothing to the calling administrator (user 1): the file exists but is not readable
            seedFileRowWithAcl(file.id(), file.fileType(), file.mimeType(), file.filename(), "/ctc-map", 2L, true, false, false);
        }
        jdbcTemplate.update("UPDATE files SET gps_location_id = ?, original_datetime = ?::timestamp WHERE id = ?", gpsId, file.takenAt(), file.id());
    }
}
