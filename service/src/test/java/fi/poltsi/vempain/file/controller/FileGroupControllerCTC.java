package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link FileGroupController}.
 *
 * <p>
 * Tests all REST endpoints declared in {@code FileGroupAPI}:
 * <ul>
 * <li>POST /api/file-groups/paged – paged list</li>
 * <li>GET /api/file-groups/{id} – single group by id</li>
 * <li>POST /api/file-groups – create</li>
 * <li>PUT /api/file-groups – update</li>
 * </ul>
 */
class FileGroupControllerCTC extends AbstractControllerCTC {

    @BeforeEach
    void cleanFileGroups() {
        // CASCADE removes file_group_files rows automatically
        jdbcTemplate.execute("TRUNCATE TABLE file_group RESTART IDENTITY CASCADE");
    }

    // -----------------------------------------------------------------------
    // POST /api/file-groups/paged
    // -----------------------------------------------------------------------

    @Test
    void getFileGroupsReturnsOkWithEmptyPageWhenNoGroupsExist() throws Exception {
        doPost("/file-groups/paged", "{\"page\":0,\"size\":10}").andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void getFileGroupsReturnsOkWithDataWhenGroupsExist() throws Exception {
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/photos/2025", "Holiday Photos",
                "Best photos from 2025");
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/videos/2025", "Holiday Videos",
                "Best videos from 2025");

        doPost("/file-groups/paged", "{\"page\":0,\"size\":10}").andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void getFileGroupsSupportsSortAndSearch() throws Exception {
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/alpha", "Alpha Group", "First group");
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/beta", "Beta Group", "Second group");

        doPost("/file-groups/paged", "{\"page\":0,\"size\":10,\"sort_by\":\"group_name\",\"direction\":\"ASC\",\"search\":\"Alpha\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].group_name", is("Alpha Group")));
    }

    @Test
    void getFileGroupsSupportsSortingByFileCount() throws Exception {
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/z-empty", "Empty Group", "No files");
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/a-with-file", "File Group", "One file");

        Long groupWithFileId = jdbcTemplate.queryForObject("SELECT id FROM file_group WHERE path = '/a-with-file'", Long.class);
        jdbcTemplate.update("INSERT INTO file_group_files(file_group_id, file_id) VALUES (?, ?)", groupWithFileId, 1L);

        doPost("/file-groups/paged", "{\"page\":0,\"size\":10,\"sort_by\":\"file_count\",\"direction\":\"DESC\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].group_name", is("File Group")))
            .andExpect(jsonPath("$.content[0].file_count", is(1)))
            .andExpect(jsonPath("$.content[0].exported_file_count", is(0)));
    }

    @Test
    void getFileGroupsCountsTheExportedFilesSeparately() throws Exception {
        seedFileRow(901L, "IMAGE", "image/jpeg", "exported.jpg", "/images");
        seedFileRow(902L, "IMAGE", "image/jpeg", "original-only.jpg", "/images");
        try {
            jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/a-partly-exported", "Partly Exported", "");
            jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/b-not-exported", "Not Exported", "");
            Long partlyExportedId = jdbcTemplate.queryForObject("SELECT id FROM file_group WHERE path = '/a-partly-exported'", Long.class);
            Long notExportedId = jdbcTemplate.queryForObject("SELECT id FROM file_group WHERE path = '/b-not-exported'", Long.class);
            jdbcTemplate.update("INSERT INTO file_group_files(file_group_id, file_id) VALUES (?, ?)", partlyExportedId, 901L);
            jdbcTemplate.update("INSERT INTO file_group_files(file_group_id, file_id) VALUES (?, ?)", partlyExportedId, 902L);
            jdbcTemplate.update("INSERT INTO file_group_files(file_group_id, file_id) VALUES (?, ?)", notExportedId, 902L);
            jdbcTemplate.update(
                    "INSERT INTO export_files(file_id, filename, file_path, mimetype, filesize, sha256sum, created)" + " VALUES (?, ?, ?, ?, ?, ?, NOW())",
                    901L, "exported.jpg", "/images", "image/jpeg", 10L, "0".repeat(64));

            // Only files with an exported file are transferred by a publish, so the exported count is what sizes it
            doPost("/file-groups/paged", "{\"page\":0,\"size\":10,\"sort_by\":\"exported_file_count\",\"direction\":\"DESC\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].group_name", is("Partly Exported")))
                .andExpect(jsonPath("$.content[0].file_count", is(2)))
                .andExpect(jsonPath("$.content[0].exported_file_count", is(1)))
                .andExpect(jsonPath("$.content[1].group_name", is("Not Exported")))
                .andExpect(jsonPath("$.content[1].file_count", is(1)))
                .andExpect(jsonPath("$.content[1].exported_file_count", is(0)));
        } finally {
            // The export row goes with the file (ON DELETE CASCADE)
            deleteFileRow(901L);
            deleteFileRow(902L);
        }
    }

    // -----------------------------------------------------------------------
    // GET /api/file-groups/{id}
    // -----------------------------------------------------------------------

    @Test
    void getFileGroupByIdReturns404WhenNotFound() throws Exception {
        doGet("/file-groups/99999").andExpect(status().isNotFound());
    }

    @Test
    void getFileGroupByIdReturns200WhenExists() throws Exception {
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/test/path", "Test Group", "A test group");
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);

        doGet("/file-groups/" + id).andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(id.intValue())))
            .andExpect(jsonPath("$.group_name", is("Test Group")))
            .andExpect(jsonPath("$.path", is("/test/path")));
    }

    // -----------------------------------------------------------------------
    // POST /api/file-groups  (create)
    // -----------------------------------------------------------------------

    @Test
    void addFileGroupReturns201WhenRequestIsValid() throws Exception {
        doPost("/file-groups", "{\"path\":\"/new/path\",\"group_name\":\"New Group\",\"description\":\"desc\",\"file_ids\":[]}").andExpect(status().isCreated())
            .andExpect(jsonPath("$.group_name", is("New Group")))
            .andExpect(jsonPath("$.path", is("/new/path")));
    }

    @Test
    void addFileGroupReturns400WhenGroupNameIsMissing() throws Exception {
        // group_name is @NotNull — omitting it triggers validation error
        doPost("/file-groups", "{\"path\":\"/new/path\",\"file_ids\":[]}").andExpect(status().isBadRequest());
    }

    @Test
    void addFileGroupReturns400WhenFileIdsIsNull() throws Exception {
        // file_ids is @NotNull
        doPost("/file-groups", "{\"path\":\"/new/path\",\"group_name\":\"g\"}").andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // PUT /api/file-groups  (update)
    // -----------------------------------------------------------------------

    @Test
    void updateFileGroupReturns200WhenGroupExistsAndRequestIsValid() throws Exception {
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/original", "Original Name", "original desc");
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);

        String updateBody = """
                {
                  "id": %d,
                  "path": "/updated",
                  "group_name": "Updated Name",
                  "description": "updated desc",
                  "file_ids": []
                }
                """.formatted(id);

        doPut("/file-groups", updateBody).andExpect(status().isOk())
            .andExpect(jsonPath("$.group_name", is("Updated Name")))
            .andExpect(jsonPath("$.path", is("/updated")));
    }

    @Test
    void updateFileGroupReturns404WhenGroupDoesNotExist() throws Exception {
        // Spring MVC 6.2+ maps EntityNotFoundException to 404
        String body = """
                {
                  "id": 99999,
                  "path": "/updated",
                  "group_name": "Updated Name",
                  "file_ids": []
                }
                """;
        doPut("/file-groups", body).andExpect(status().isNotFound());
    }
}
