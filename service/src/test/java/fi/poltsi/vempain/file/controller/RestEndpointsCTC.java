package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"vempain.app.frontend-url=http://localhost:3000",
		"vempain.original-root-directory=/tmp",
		"vempain.export-root-directory=/tmp"
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RestEndpointsCTC {

	@Autowired
	private MockMvc mockMvc;

	record EndpointCase(String method, String path, String body, boolean csrf) {
	}

	@Test
	void allEndpoints_requireAuthentication_andAreReachableWhenAuthenticated() throws Exception {
		var cases = List.of(
				new EndpointCase("POST", "/api/file-groups/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/file-groups/1", null, false),
				new EndpointCase("POST", "/api/file-groups", "{\"path\":\"/p\",\"group_name\":\"g\",\"file_ids\":[]}", true),
				new EndpointCase("PUT", "/api/file-groups", "{\"id\":1,\"path\":\"/p\",\"group_name\":\"g\",\"file_ids\":[]}", true),
				new EndpointCase("POST", "/api/scan-files", "{\"original_directory\":\"does-not-exist\"}", true),
				new EndpointCase("GET", "/api/location/1", null, false),
				new EndpointCase("GET", "/api/location/guards", null, false),
				new EndpointCase("GET", "/api/location/guards/1", null, false),
				new EndpointCase("POST", "/api/location/guards", "{\"guard_type\":\"CIRCLE\",\"primary_coordinate\":{\"latitude\":60.1,\"longitude\":24.9},\"radius\":100}", true),
				new EndpointCase("PUT", "/api/location/guards", "{\"id\":1,\"guard_type\":\"SQUARE\",\"primary_coordinate\":{\"latitude\":60.1,\"longitude\":24.9},\"secondary_coordinate\":{\"latitude\":60.2,\"longitude\":25.0}}", true),
				new EndpointCase("DELETE", "/api/location/guards/1", null, true),
				new EndpointCase("POST", "/api/path-completion", "{\"type\":\"ORIGINAL\",\"path\":\"/\"}", true),
				new EndpointCase("POST", "/api/publish/file-group", "{\"file_group_id\":1,\"gallery_name\":\"g\",\"gallery_description\":\"d\"}", true),
				new EndpointCase("GET", "/api/publish/all-file-groups", null, false),
				new EndpointCase("GET", "/api/publish/progress", null, false),
				new EndpointCase("GET", "/api/tags", null, false),
				new EndpointCase("GET", "/api/tags/1", null, false),
				new EndpointCase("POST", "/api/tags", "{\"tag_name\":\"nature\"}", true),
				new EndpointCase("PUT", "/api/tags", "{\"id\":1,\"tag_name\":\"nature\"}", true),
				new EndpointCase("DELETE", "/api/tags/1", null, true),
				new EndpointCase("POST", "/api/files/archive/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/archive/1", null, false),
				new EndpointCase("DELETE", "/api/files/archive/1", null, true),
				new EndpointCase("POST", "/api/files/audio/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/audio/1", null, false),
				new EndpointCase("DELETE", "/api/files/audio/1", null, true),
				new EndpointCase("POST", "/api/files/binary/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/binary/1", null, false),
				new EndpointCase("DELETE", "/api/files/binary/1", null, true),
				new EndpointCase("POST", "/api/files/data/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/data/1", null, false),
				new EndpointCase("DELETE", "/api/files/data/1", null, true),
				new EndpointCase("POST", "/api/files/document/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/document/1", null, false),
				new EndpointCase("DELETE", "/api/files/document/1", null, true),
				new EndpointCase("POST", "/api/files/executable/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/executable/1", null, false),
				new EndpointCase("DELETE", "/api/files/executable/1", null, true),
				new EndpointCase("POST", "/api/files/font/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/font/1", null, false),
				new EndpointCase("DELETE", "/api/files/font/1", null, true),
				new EndpointCase("POST", "/api/files/icon/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/icon/1", null, false),
				new EndpointCase("DELETE", "/api/files/icon/1", null, true),
				new EndpointCase("POST", "/api/files/image/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/image/1", null, false),
				new EndpointCase("DELETE", "/api/files/image/1", null, true),
				new EndpointCase("POST", "/api/files/interactive/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/interactive/1", null, false),
				new EndpointCase("DELETE", "/api/files/interactive/1", null, true),
				new EndpointCase("POST", "/api/files/thumb/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/thumb/1", null, false),
				new EndpointCase("DELETE", "/api/files/thumb/1", null, true),
				new EndpointCase("POST", "/api/files/vector/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/vector/1", null, false),
				new EndpointCase("DELETE", "/api/files/vector/1", null, true),
				new EndpointCase("POST", "/api/files/video/paged", "{\"page\":0,\"size\":10}", true),
				new EndpointCase("GET", "/api/files/video/1", null, false),
				new EndpointCase("DELETE", "/api/files/video/1", null, true)
		);

		for (var endpoint : cases) {
			perform(endpoint, false).andExpect(status().is4xxClientError());
		}

		for (var endpoint : cases) {
			var result = perform(endpoint, true).andReturn()
			                                    .getResponse();
			assertThat(result.getStatus()).isNotEqualTo(401);
		}
	}

	private ResultActions perform(EndpointCase endpoint, boolean authenticated) throws Exception {
		MockHttpServletRequestBuilder builder = switch (endpoint.method()) {
			case "GET" -> MockMvcRequestBuilders.get(endpoint.path());
			case "POST" -> MockMvcRequestBuilders.post(endpoint.path());
			case "PUT" -> MockMvcRequestBuilders.put(endpoint.path());
			case "DELETE" -> MockMvcRequestBuilders.delete(endpoint.path());
			default -> throw new IllegalArgumentException("Unsupported method " + endpoint.method());
		};

		if (authenticated) {
			builder = builder.with(user("ctc-user").roles("USER"));
		}
		if (endpoint.csrf()) {
			builder = builder.with(csrf());
		}
		if (endpoint.body() != null) {
			builder.contentType(MediaType.APPLICATION_JSON)
			       .content(endpoint.body());
		}
		return mockMvc.perform(builder);
	}
}
