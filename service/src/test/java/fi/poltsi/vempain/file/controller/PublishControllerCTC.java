package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.admin.api.response.file.FileIngestUserResponse;
import fi.poltsi.vempain.file.feign.VempainAdminFileIngestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link PublishController}.
 *
 * <p>Tests all REST endpoints declared in {@code PublishAPI}:
 * <ul>
 *   <li>POST /api/publish/file            – publish a single file (no gallery)</li>
 *   <li>POST /api/publish/file-group      – publish a single file group</li>
 *   <li>POST /api/publish/all-file-groups – publish all file groups</li>
 *   <li>GET  /api/publish/users           – admin users that can be granted privileges on published resources</li>
 * </ul>
 *
 * <p>Publish operations run as background tasks and answer 202 with a {@code TaskAcceptedResponse};
 * the task itself is followed through {@code TaskAPI} (see {@link TaskControllerCTC}).
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
	void publishFile_returns404_whenFileDoesNotExist() throws Exception {
		doPost("/publish/file", "{\"file_id\":99999}")
				.andExpect(status().isNotFound());
	}

	/**
	 * {@code files} uses JOINED inheritance, so a loadable file needs its type-specific child row as well.
	 */
	private void seedImageFile(long id, String filename, boolean modify) {
		seedFileRowWithAcl(id, "IMAGE", "image/jpeg", filename, "/pub", 1L, true, modify, modify);
		jdbcTemplate.update("INSERT INTO image_files (id, width, height, color_depth, dpi, group_label) VALUES (?, 640, 480, 24, 72, 'publish-ctc')", id);
	}

	private void deleteImageFile(long id) {
		jdbcTemplate.update("DELETE FROM image_files WHERE id = ?", id);
		deleteFileRow(id);
	}

	@Test
	void publishFile_returns403_whenCallerMayNotModifyTheFile() throws Exception {
		seedImageFile(9301L, "readonly.jpg", false);
		try {
			doPost("/publish/file", "{\"file_id\":9301}")
					.andExpect(status().isForbidden());
		} finally {
			deleteImageFile(9301L);
		}
	}

	@Test
	void publishFile_returns202_withAclGrantees() throws Exception {
		seedImageFile(9302L, "single.jpg", true);
		try {
			doPost("/publish/file",
				   "{\"file_id\":9302,\"acls\":[{\"user_id\":5,\"read_privilege\":true}]}")
					.andExpect(status().isAccepted())
					.andExpect(jsonPath("$.task_id", notNullValue()))
					.andExpect(jsonPath("$.type").value("PUBLISH_FILE"))
					.andExpect(jsonPath("$.title").value("Publish file single.jpg"))
					.andExpect(jsonPath("$.total_steps").value(1));
		} finally {
			deleteImageFile(9302L);
		}
	}

	@Test
	void publishFile_returns400_whenAnAclEntryIsInvalid() throws Exception {
		seedImageFile(9303L, "bad-acl.jpg", true);
		try {
			doPost("/publish/file", "{\"file_id\":9303,\"acls\":[{\"user_id\":5}]}")
					.andExpect(status().isBadRequest());
		} finally {
			deleteImageFile(9303L);
		}
	}

	// -----------------------------------------------------------------------
	// POST /api/publish/file-group
	// -----------------------------------------------------------------------

	@Test
	void publishFileGroup_returns404_whenFileGroupDoesNotExist() throws Exception {
		doPost("/publish/file-group",
		       "{\"file_group_id\":99999}")
				.andExpect(status().isNotFound());
	}

	@Test
	void publishFileGroup_returns202_whenFileGroupExists() throws Exception {
		// countFilesInGroup uses countById which returns 1 when the group exists
		jdbcTemplate.update(
				"INSERT INTO file_group (path, group_name, description) VALUES (?, ?, ?)",
				"/pub/path", "Publish Group", "A group to publish");
		Long groupId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);

		doPost("/publish/file-group",
		       "{\"file_group_id\":" + groupId + ",\"gallery_name\":\"My Gallery\",\"gallery_description\":\"Desc\"}")
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.task_id", notNullValue()))
				.andExpect(jsonPath("$.type").value("PUBLISH_FILE_GROUP"))
				.andExpect(jsonPath("$.title").value("Publish file group My Gallery"))
				.andExpect(jsonPath("$.status", notNullValue()));
	}

	@Test
	void publishFileGroup_returns400_whenAnAclEntryGrantsNoPrivilegeOrNamesNoUser() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO file_group (path, group_name, description) VALUES (?, ?, ?)",
				"/pub/acl", "Acl Group", "A group to publish");
		Long groupId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);

		doPost("/publish/file-group",
			   "{\"file_group_id\":" + groupId + ",\"acls\":[{\"user_id\":5}]}")
				.andExpect(status().isBadRequest());
		doPost("/publish/file-group",
			   "{\"file_group_id\":" + groupId + ",\"acls\":[{\"read_privilege\":true}]}")
				.andExpect(status().isBadRequest());
		doPost("/publish/file-group",
			   "{\"file_group_id\":" + groupId + ",\"acls\":[{\"user_id\":-5,\"read_privilege\":true}]}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void publishFileGroup_returns202_withAclGrantees() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO file_group (path, group_name, description) VALUES (?, ?, ?)",
				"/pub/acl-ok", "Acl Group", "A group to publish");
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
	void publishAllFileGroups_returns202_whenNoGroupsExist() throws Exception {
		doPost("/publish/all-file-groups", "{}")
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.type").value("PUBLISH_ALL_FILE_GROUPS"))
				.andExpect(jsonPath("$.total_steps").value(0));
	}

	@Test
	void publishAllFileGroups_returns202_withScheduledCount_whenGroupsExist() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO file_group (path, group_name, description) VALUES (?,?,?)",
				"/g1", "Group 1", "desc 1");
		jdbcTemplate.update(
				"INSERT INTO file_group (path, group_name, description) VALUES (?,?,?)",
				"/g2", "Group 2", "desc 2");

		doPost("/publish/all-file-groups", "{\"acls\":[{\"user_id\":5,\"read_privilege\":true}]}")
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.task_id", notNullValue()))
				.andExpect(jsonPath("$.total_steps", greaterThanOrEqualTo(2)));
	}

	@Test
	void publishAllFileGroups_returns400_whenAnAclEntryIsInvalid() throws Exception {
		doPost("/publish/all-file-groups", "{\"acls\":[{\"user_id\":5}]}")
				.andExpect(status().isBadRequest());
	}

	// -----------------------------------------------------------------------
	// GET /api/publish/users
	// -----------------------------------------------------------------------

	@Test
	void listPublishUsers_proxiesTheAdminBackendUserListing() throws Exception {
		when(vempainAdminFileIngestClient.listIngestUsers())
				.thenReturn(ResponseEntity.ok(List.of(FileIngestUserResponse.builder()
																			.id(12L)
																			.loginName("arnold")
																			.name("Arnold")
																			.nick("Ahnold")
																			.build())));

		doGet("/publish/users")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(12))
				.andExpect(jsonPath("$[0].login_name").value("arnold"))
				.andExpect(jsonPath("$[0].name").value("Arnold"))
				.andExpect(jsonPath("$[0].nick").value("Ahnold"));
	}

	@Test
	void listPublishUsers_requiresAuthentication() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/publish/users"))
			   .andExpect(status().isUnauthorized());
	}

}

