package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.api.request.TagRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for request JSON names. These strings intentionally remain
 * explicit so a mapper or annotation regression fails immediately.
 */
class RequestContractCTC {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void tagRequestUsesSnakeCaseForEveryPublicField() throws Exception {
		var request = new TagRequest(17L, "tag", "tag-de", "tag-en", "tag-es", "tag-fi", "tag-sv");

		assertThat(objectMapper.writeValueAsString(request))
				.isEqualTo("{\"id\":17,\"tag_name\":\"tag\",\"tag_name_de\":\"tag-de\",\"tag_name_en\":\"tag-en\",\"tag_name_es\":\"tag-es\",\"tag_name_fi\":\"tag-fi\",\"tag_name_sv\":\"tag-sv\"}");
	}

	@Test
	void tagRequestReadsSnakeCasePayload() throws Exception {
		var request = objectMapper.readValue(
				"{\"id\":17,\"tag_name\":\"tag\",\"tag_name_de\":\"tag-de\",\"tag_name_en\":\"tag-en\",\"tag_name_es\":\"tag-es\",\"tag_name_fi\":\"tag-fi\",\"tag_name_sv\":\"tag-sv\"}",
				TagRequest.class);

		assertThat(request.getTagName()).isEqualTo("tag");
		assertThat(request.getTagNameDe()).isEqualTo("tag-de");
		assertThat(request.getTagNameSv()).isEqualTo("tag-sv");
	}
}
