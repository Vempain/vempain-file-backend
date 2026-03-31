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
 * <p>Tests all REST endpoints declared in {@code FileGroupAPI}:
 * <ul>
 *   <li>POST /api/file-groups/paged  – paged list</li>
 *   <li>GET  /api/file-groups/{id}   – single group by id</li>
 *   <li>POST /api/file-groups        – create</li>
 *   <li>PUT  /api/file-groups        – update</li>
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
	void getFileGroups_returnsOkWithEmptyPage_whenNoGroupsExist() throws Exception {
		doPost("/file-groups/paged", "{\"page\":0,\"size\":10}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content", hasSize(0)));
	}

	@Test
	void getFileGroups_returnsOkWithData_whenGroupsExist() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO file_group (path, group_name, description) VALUES (?, ?, ?)",
				"/photos/2025", "Holiday Photos", "Best photos from 2025");
		jdbcTemplate.update(
				"INSERT INTO file_group (path, group_name, description) VALUES (?, ?, ?)",
				"/videos/2025", "Holiday Videos", "Best videos from 2025");

		doPost("/file-groups/paged", "{\"page\":0,\"size\":10}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
	}

	@Test
	void getFileGroups_supportsSortAndSearch() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO file_group (path, group_name, description) VALUES (?, ?, ?)",
				"/alpha", "Alpha Group", "First group");
		jdbcTemplate.update(
				"INSERT INTO file_group (path, group_name, description) VALUES (?, ?, ?)",
				"/beta", "Beta Group", "Second group");

		doPost("/file-groups/paged",
		       "{\"page\":0,\"size\":10,\"sort_by\":\"group_name\",\"direction\":\"ASC\",\"search\":\"Alpha\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].group_name", is("Alpha Group")));
	}

	// -----------------------------------------------------------------------
	// GET /api/file-groups/{id}
	// -----------------------------------------------------------------------

	@Test
	void getFileGroupById_returns404_whenNotFound() throws Exception {
		doGet("/file-groups/99999")
				.andExpect(status().isNotFound());
	}

	@Test
	void getFileGroupById_returns200_whenExists() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO file_group (path, group_name, description) VALUES (?, ?, ?)",
				"/test/path", "Test Group", "A test group");
		Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_group", Long.class);

		doGet("/file-groups/" + id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(id.intValue())))
				.andExpect(jsonPath("$.group_name", is("Test Group")))
				.andExpect(jsonPath("$.path", is("/test/path")));
	}

	// -----------------------------------------------------------------------
	// POST /api/file-groups  (create)
	// -----------------------------------------------------------------------

	@Test
	void addFileGroup_returns201_whenRequestIsValid() throws Exception {
		doPost("/file-groups",
		       "{\"path\":\"/new/path\",\"group_name\":\"New Group\",\"description\":\"desc\",\"file_ids\":[]}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.group_name", is("New Group")))
				.andExpect(jsonPath("$.path", is("/new/path")));
	}

	@Test
	void addFileGroup_returns400_whenGroupNameIsMissing() throws Exception {
		// group_name is @NotNull — omitting it triggers validation error
		doPost("/file-groups",
		       "{\"path\":\"/new/path\",\"file_ids\":[]}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void addFileGroup_returns400_whenFileIdsIsNull() throws Exception {
		// file_ids is @NotNull
		doPost("/file-groups",
		       "{\"path\":\"/new/path\",\"group_name\":\"g\"}")
				.andExpect(status().isBadRequest());
	}

	// -----------------------------------------------------------------------
	// PUT /api/file-groups  (update)
	// -----------------------------------------------------------------------

	@Test
	void updateFileGroup_returns200_whenGroupExistsAndRequestIsValid() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO file_group (path, group_name, description) VALUES (?, ?, ?)",
				"/original", "Original Name", "original desc");
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

		doPut("/file-groups", updateBody)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.group_name", is("Updated Name")))
				.andExpect(jsonPath("$.path", is("/updated")));
	}

	@Test
	void updateFileGroup_returns404_whenGroupDoesNotExist() throws Exception {
		// Spring MVC 6.2+ maps EntityNotFoundException to 404
		String body = """
				{
				  "id": 99999,
				  "path": "/updated",
				  "group_name": "Updated Name",
				  "file_ids": []
				}
				""";
		doPut("/file-groups", body)
				.andExpect(status().isNotFound());
	}
}

