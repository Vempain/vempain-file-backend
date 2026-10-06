package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link PublishController}.
 *
 * <p>Tests all REST endpoints declared in {@code PublishAPI}:
 * <ul>
 *   <li>POST /api/publish/file-group      – publish a single file group</li>
 *   <li>GET  /api/publish/all-file-groups – publish all file groups</li>
 * </ul>
 *
 * <p>Publish operations run as background tasks and answer 202 with a {@code TaskAcceptedResponse};
 * the task itself is followed through {@code TaskAPI} (see {@link TaskControllerCTC}).
 */
class PublishControllerCTC extends AbstractControllerCTC {

	@BeforeEach
	void cleanFileGroups() {
		jdbcTemplate.execute("TRUNCATE TABLE file_group RESTART IDENTITY CASCADE");
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

	// -----------------------------------------------------------------------
	// GET /api/publish/all-file-groups
	// -----------------------------------------------------------------------

	@Test
	void publishAllFileGroups_returns202_whenNoGroupsExist() throws Exception {
		doGet("/publish/all-file-groups")
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

		doGet("/publish/all-file-groups")
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.task_id", notNullValue()))
				.andExpect(jsonPath("$.total_steps", greaterThanOrEqualTo(2)));
	}

}

