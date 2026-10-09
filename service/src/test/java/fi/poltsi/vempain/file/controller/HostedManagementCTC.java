package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) verifying that the user, unit and ACL management endpoints of {@code vempain-auth-core} are hosted by this
 * service for its own user base: {@code /api/content-management/users}, {@code /units} and {@code /acls}.
 */
class HostedManagementCTC extends AbstractControllerCTC {

	@Test
	void managementEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/content-management/users"))
			   .andExpect(status().isUnauthorized());
		mockMvc.perform(get("/content-management/units"))
			   .andExpect(status().isUnauthorized());
		mockMvc.perform(get("/content-management/acls"))
			   .andExpect(status().isUnauthorized());
	}

	@Test
	void administratorListsUsersUnitsAndAcls() throws Exception {
		doGet("/content-management/users")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.login_name == 'admin')]", hasSize(1)));
		doGet("/content-management/units")
				.andExpect(status().isOk());
		doGet("/content-management/acls")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
	}

	@Test
	void unitsAreCreatedWithMembersAndCyclesAreRejected() throws Exception {
		var created = doPost("/content-management/units",
							 "{\"name\":\"file-unit\",\"description\":\"hosted\",\"acls\":[{\"user\":1,\"read_privilege\":true,\"create_privilege\":true,"
							 + "\"modify_privilege\":true,\"delete_privilege\":true}],\"user_ids\":[1],\"unit_ids\":[]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("file-unit"))
				.andExpect(jsonPath("$.user_ids[0]").value(1))
				.andReturn()
				.getResponse()
				.getContentAsString();
		var unitId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

		doPut("/content-management/units/" + unitId,
			  "{\"name\":\"file-unit\",\"description\":\"self\",\"acls\":[{\"user\":1,\"read_privilege\":true}],\"unit_ids\":[" + unitId + "]}")
				.andExpect(status().isBadRequest());

		doGet("/content-management/units/" + unitId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unit_ids", hasSize(0)))
				.andExpect(jsonPath("$.user_ids[0]").value(1));
		doGet("/content-management/users/1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unit_ids[?(@ == " + unitId + ")]", hasSize(1)));

		jdbcTemplate.update("DELETE FROM user_unit WHERE unit_id = ?", unitId);
		jdbcTemplate.update("DELETE FROM unit WHERE id = ?", unitId);
	}
}
