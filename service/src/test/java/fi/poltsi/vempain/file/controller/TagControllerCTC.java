package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link TagController}.
 *
 * <p>Tests all REST endpoints declared in {@code TagAPI}:
 * <ul>
 *   <li>GET    /api/tags      – list all tags</li>
 *   <li>GET    /api/tags/{id} – single tag</li>
 *   <li>POST   /api/tags      – create</li>
 *   <li>PUT    /api/tags      – update</li>
 *   <li>DELETE /api/tags/{id} – delete</li>
 * </ul>
 */
class TagControllerCTC extends AbstractControllerCTC {

	private static final String VALID_TAG_BODY = """
			{
			  "tag_name": "nature",
			  "tag_name_de": "Natur",
			  "tag_name_en": "nature",
			  "tag_name_es": "naturaleza",
			  "tag_name_fi": "luonto",
			  "tag_name_sv": "natur"
			}
			""";

	@BeforeEach
	void cleanTags() {
		// file_tags has FK to tags; CASCADE removes it
		jdbcTemplate.execute("TRUNCATE TABLE tags RESTART IDENTITY CASCADE");
	}

	// -----------------------------------------------------------------------
	// GET /api/tags
	// -----------------------------------------------------------------------

	@Test
	void getAllTags_returnsEmptyList_whenNoTagsExist() throws Exception {
		doGet("/tags")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void getAllTags_returnsListWithTags_whenTagsExist() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)",
				"animal", "Tier", "animal", "animal", "eläin", "djur");
		jdbcTemplate.update(
				"INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)",
				"landscape", "Landschaft", "landscape", "paisaje", "maisema", "landskap");

		doGet("/tags")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(2)));
	}

	// -----------------------------------------------------------------------
	// GET /api/tags/{id}
	// -----------------------------------------------------------------------

	@Test
	void getTagById_returns200_whenTagExists() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)",
				"flower", "Blume", "flower", "flor", "kukka", "blomma");
		Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM tags", Long.class);

		doGet("/tags/" + id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(id.intValue())))
				.andExpect(jsonPath("$.tag_name", is("flower")));
	}

	@Test
	void getTagById_returns400_whenTagNotFound() throws Exception {
		// TagService throws IllegalArgumentException("Tag not found") → GlobalExceptionHandler → 400
		doGet("/tags/99999")
				.andExpect(status().isBadRequest());
	}

	// -----------------------------------------------------------------------
	// POST /api/tags  (create)
	// -----------------------------------------------------------------------

	@Test
	void createTag_returns200_whenRequestIsValid() throws Exception {
		doPost("/tags", VALID_TAG_BODY)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.tag_name", is("nature")));
	}

	@Test
	void createTag_returns400_whenTagNameIsBlank() throws Exception {
		// tag_name is @NotBlank
		doPost("/tags",
		       "{\"tag_name\":\"\",\"tag_name_de\":\"d\",\"tag_name_en\":\"e\",\"tag_name_es\":\"e\",\"tag_name_fi\":\"f\",\"tag_name_sv\":\"s\"}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void createTag_returns400_whenRequiredFieldsMissing() throws Exception {
		// Missing tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv
		doPost("/tags", "{\"tag_name\":\"test\"}")
				.andExpect(status().isBadRequest());
	}

	// -----------------------------------------------------------------------
	// PUT /api/tags  (update)
	// -----------------------------------------------------------------------

	@Test
	void updateTag_returns200_whenTagExistsAndRequestIsValid() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)",
				"sky", "Himmel", "sky", "cielo", "taivas", "himmel");
		Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM tags", Long.class);

		String updateBody = """
				{
				  "id": %d,
				  "tag_name": "blue-sky",
				  "tag_name_de": "Blauer Himmel",
				  "tag_name_en": "blue sky",
				  "tag_name_es": "cielo azul",
				  "tag_name_fi": "sininen taivas",
				  "tag_name_sv": "blå himmel"
				}
				""".formatted(id);

		doPut("/tags", updateBody)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tag_name", is("blue-sky")));
	}

	@Test
	void updateTag_returns400_whenIdIsNullInRequest() throws Exception {
		// id=null → service throws IllegalArgumentException → GlobalExceptionHandler → 400
		String body = """
				{
				  "tag_name": "updated",
				  "tag_name_de": "aktualisiert",
				  "tag_name_en": "updated",
				  "tag_name_es": "actualizado",
				  "tag_name_fi": "päivitetty",
				  "tag_name_sv": "uppdaterad"
				}
				""";
		doPut("/tags", body)
				.andExpect(status().isBadRequest());
	}

	// -----------------------------------------------------------------------
	// DELETE /api/tags/{id}
	// -----------------------------------------------------------------------

	@Test
	void deleteTag_returns204_whenTagExists() throws Exception {
		jdbcTemplate.update(
				"INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)",
				"delete-me", "Löschen", "delete", "eliminar", "poista", "radera");
		Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM tags", Long.class);

		doDelete("/tags/" + id)
				.andExpect(status().isNoContent());
	}

	@Test
	void deleteTag_returns204_whenTagDoesNotExist() throws Exception {
		// deleteById does not throw if entity is missing in JPA default impl
		doDelete("/tags/99999")
				.andExpect(status().isNoContent());
	}
}

