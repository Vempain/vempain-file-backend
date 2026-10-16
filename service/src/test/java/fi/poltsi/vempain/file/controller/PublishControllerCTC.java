package fi.poltsi.vempain.file.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import fi.poltsi.vempain.admin.api.response.file.FileIngestUnitResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestUserResponse;
import fi.poltsi.vempain.file.feign.VempainAdminFileIngestClient;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link PublishController}.
 *
 * <p>
 * Tests all REST endpoints declared in {@code PublishAPI}:
 * <ul>
 * <li>POST /api/publish/file – publish a single file (no gallery)</li>
 * <li>POST /api/publish/file-group – publish a single file group</li>
 * <li>POST /api/publish/all-file-groups – publish all file groups</li>
 * <li>GET /api/publish/users – admin users that can be granted privileges on published resources</li>
 * <li>GET /api/publish/units – admin units that can be granted privileges on published resources</li>
 * </ul>
 *
 * <p>
 * Publish operations run as background tasks and answer 202 with a {@code TaskAcceptedResponse}; the task itself is followed through {@code TaskAPI} (see
 * {@link TaskControllerCTC}).
 */
class PublishControllerCTC extends AbstractControllerCTC {

    /**
     * The user listing is proxied to the admin backend, which is not available in the test context.
     */
    @MockitoBean
    private VempainAdminFileIngestClient vempainAdminFileIngestClient;

    @BeforeEach
    void cleanFileGroups() {
        jdbcTemplate.execute("TRUNCATE TABLE file_group RESTART IDENTITY CASCADE");
    }

    // -----------------------------------------------------------------------
    // POST /api/publish/file
    // -----------------------------------------------------------------------

    @Test
    void publishFileReturns404WhenFileDoesNotExist() throws Exception {
        doPost("/publish/file", "{\"file_id\":99999}").andExpect(status().isNotFound());
    }

    /**
     * {@code files} uses JOINED inheritance, so a loadable file needs its type-specific child row as well.
     */
    private void seedImageFile(long id, String filename, boolean modify) {
        seedFileRowWithAcl(id, "IMAGE", "image/jpeg", filename, "/pub", 1L, true, modify, modify);
        jdbcTemplate.update("INSERT INTO image_files(id, width, height, color_depth, dpi, group_label) VALUES (?, 640, 480, 24, 72, 'publish-ctc')", id);
    }

    private void deleteImageFile(long id) {
        jdbcTemplate.update("DELETE FROM image_files WHERE id = ?", id);
        deleteFileRow(id);
    }

    @Test
    void publishFileReturns403WhenCallerMayNotModifyTheFile() throws Exception {
        seedImageFile(9301L, "readonly.jpg", false);
        try {
            doPost("/publish/file", "{\"file_id\":9301}").andExpect(status().isForbidden());
        } finally {
            deleteImageFile(9301L);
        }
    }

    @Test
    void publishFilesReturns404WhenAnyRequestedFileDoesNotExist() throws Exception {
        doPost("/publish/files", "{\"file_ids\":[9304,9305]}").andExpect(status().isNotFound());
    }

    @Test
    void publishFilesReturns403WhenAnyRequestedFileMayNotBeModified() throws Exception {
        seedImageFile(9306L, "readonly-selected.jpg", false);
        try {
            doPost("/publish/files", "{\"file_ids\":[9306]}").andExpect(status().isForbidden());
        } finally {
            deleteImageFile(9306L);
        }
    }

    @Test
    void publishFileReturns202WithAclGrantees() throws Exception {
        seedImageFile(9302L, "single.jpg", true);
        try {
            doPost("/publish/file", "{\"file_id\":9302,\"acls\":[{\"user_id\":5,\"read_privilege\":true}]}").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.task_id", notNullValue()))
                .andExpect(jsonPath("$.type").value("PUBLISH_FILE"))
                .andExpect(jsonPath("$.title").value("Publish file single.jpg"))
                .andExpect(jsonPath("$.total_steps").value(1));
        } finally {
            deleteImageFile(9302L);
        }
    }

    @Test
    void publishFileReturns400WhenAnAclEntryIsInvalid() throws Exception {
        seedImageFile(9303L, "bad-acl.jpg", true);
        try {
            doPost("/publish/file", "{\"file_id\":9303,\"acls\":[{\"user_id\":5}]}").andExpect(status().isBadRequest());
        } finally {
            deleteImageFile(9303L);
        }
    }

    // -----------------------------------------------------------------------
    // POST /api/publish/file-group
    // -----------------------------------------------------------------------

    @Test
    void publishFileGroupReturns404WhenFileGroupDoesNotExist() throws Exception {
        doPost("/publish/file-group", "{\"file_group_id\":99999}").andExpect(status().isNotFound());
    }

    @Test
    void publishFileGroupReturns202WhenFileGroupExists() throws Exception {
        // A group without files still exists: the task is accepted and reports that there is nothing to publish
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/pub/path", "Publish Group", "A group to publish");
        Long groupId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);

        doPost("/publish/file-group", "{\"file_group_id\":" + groupId + ",\"gallery_name\":\"My Gallery\",\"gallery_description\":\"Desc\"}")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.task_id", notNullValue()))
            .andExpect(jsonPath("$.type").value("PUBLISH_FILE_GROUP"))
            .andExpect(jsonPath("$.title").value("Publish file group My Gallery"))
            .andExpect(jsonPath("$.status", notNullValue()));
    }

    @Test
    void publishFileGroupReturns400WhenAnAclEntryGrantsNoPrivilegeOrNamesNoUser() throws Exception {
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/pub/acl", "Acl Group", "A group to publish");
        Long groupId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);

        doPost("/publish/file-group", "{\"file_group_id\":" + groupId + ",\"acls\":[{\"user_id\":5}]}").andExpect(status().isBadRequest());
        doPost("/publish/file-group", "{\"file_group_id\":" + groupId + ",\"acls\":[{\"read_privilege\":true}]}").andExpect(status().isBadRequest());
        doPost("/publish/file-group", "{\"file_group_id\":" + groupId + ",\"acls\":[{\"user_id\":-5,\"read_privilege\":true}]}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void publishFileGroupReturns202WithAclGrantees() throws Exception {
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?, ?, ?)", "/pub/acl-ok", "Acl Group", "A group to publish");
        Long groupId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);

        doPost("/publish/file-group",
                "{\"file_group_id\":" + groupId + ",\"gallery_name\":\"Shared\",\"acls\":[{\"user_id\":5,\"read_privilege\":true,\"modify_privilege\":true}]}")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.type").value("PUBLISH_FILE_GROUP"))
            .andExpect(jsonPath("$.title").value("Publish file group Shared"));
    }

    // -----------------------------------------------------------------------
    // POST /api/publish/all-file-groups
    // -----------------------------------------------------------------------

    @Test
    void publishAllFileGroupsReturns202WhenNoGroupsExist() throws Exception {
        doPost("/publish/all-file-groups", "{}").andExpect(status().isAccepted())
            .andExpect(jsonPath("$.type").value("PUBLISH_ALL_FILE_GROUPS"))
            .andExpect(jsonPath("$.total_steps").value(0));
    }

    @Test
    void publishAllFileGroupsReturns202WithScheduledCountWhenGroupsExist() throws Exception {
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?,?,?)", "/g1", "Group 1", "desc 1");
        jdbcTemplate.update("INSERT INTO file_group(path, group_name, description) VALUES (?,?,?)", "/g2", "Group 2", "desc 2");

        doPost("/publish/all-file-groups", "{\"acls\":[{\"user_id\":5,\"read_privilege\":true}]}").andExpect(status().isAccepted())
            .andExpect(jsonPath("$.task_id", notNullValue()))
            .andExpect(jsonPath("$.total_steps", greaterThanOrEqualTo(2)));
    }

    @Test
    void publishAllFileGroupsReturns400WhenAnAclEntryIsInvalid() throws Exception {
        doPost("/publish/all-file-groups", "{\"acls\":[{\"user_id\":5}]}").andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // GET /api/publish/users
    // -----------------------------------------------------------------------

    @Test
    void listPublishUsersProxiesTheAdminBackendUserListing() throws Exception {
        when(vempainAdminFileIngestClient.listIngestUsers()).thenReturn(ResponseEntity.ok(List.of(FileIngestUserResponse.builder()
            .id(12L)
            .loginName("arnold")
            .name("Arnold")
            .nick("Ahnold")
            .build())));

        doGet("/publish/users").andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(12))
            .andExpect(jsonPath("$[0].login_name").value("arnold"))
            .andExpect(jsonPath("$[0].name").value("Arnold"))
            .andExpect(jsonPath("$[0].nick").value("Ahnold"));
    }

    @Test
    void listPublishUsersRequiresAuthentication() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/publish/users"))
            .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // GET /api/publish/units
    // -----------------------------------------------------------------------

    @Test
    void listPublishUnitsProxiesTheAdminBackendUnitListing() throws Exception {
        when(vempainAdminFileIngestClient.listIngestUnits()).thenReturn(ResponseEntity.ok(List.of(FileIngestUnitResponse.builder()
            .id(3L)
            .name("Editors")
            .description("Site editors")
            .build())));

        doGet("/publish/units").andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(3))
            .andExpect(jsonPath("$[0].name").value("Editors"))
            .andExpect(jsonPath("$[0].description").value("Site editors"));
    }

    @Test
    void listPublishUnitsRequiresAuthentication() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/publish/units"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void publishFilesReturns202WhenEveryFileMayBeModified() throws Exception {
        seedImageFile(9307L, "seven.jpg", true);
        seedImageFile(9308L, "eight.jpg", true);
        try {
            doPost("/publish/files", "{\"file_ids\":[9307,9308]}").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.task_id", notNullValue()))
                .andExpect(jsonPath("$.type").value("PUBLISH_FILES"))
                .andExpect(jsonPath("$.total_steps").value(2));
        } finally {
            deleteImageFile(9307L);
            deleteImageFile(9308L);
        }
    }
}
