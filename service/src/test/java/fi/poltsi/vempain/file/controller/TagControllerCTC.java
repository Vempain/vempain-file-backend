package fi.poltsi.vempain.file.controller;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import fi.poltsi.vempain.common.metadata.MetadataJson;
import fi.poltsi.vempain.file.tools.SampleFiles;
import fi.poltsi.vempain.file.tools.TestExifTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link TagController}.
 *
 * <p>
 * Tests all REST endpoints declared in {@code TagAPI}:
 * <ul>
 * <li>GET /api/tags – list all tags</li>
 * <li>POST /api/tags/paged – paged, searchable tag list</li>
 * <li>GET /api/tags/{id} – single tag</li>
 * <li>POST /api/tags – create</li>
 * <li>PUT /api/tags – update</li>
 * <li>DELETE /api/tags/{id} – delete</li>
 * </ul>
 */
class TagControllerCTC extends AbstractControllerCTC {

    @org.springframework.beans.factory.annotation.Autowired
    private fi.poltsi.vempain.common.task.TaskProgressStore taskProgressStore;

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
    // POST /api/tags/paged
    // -----------------------------------------------------------------------

    @Test
    void getAllTagsPageableReturnsRequestedPageAndSearchesAllLanguageFields() throws Exception {
        jdbcTemplate.update("INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)", "animal",
                "Tier", "animal", "animal", "eläin", "djur");
        jdbcTemplate.update("INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)", "landscape",
                "Landschaft", "landscape", "paisaje", "maisema", "landskap");

        doPost("/tags/paged", """
                {"page":0,"size":1,"search":"Landschaft","sort_by":"tag_name","direction":"ASC"}
                """).andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].tag_name", is("landscape")))
            .andExpect(jsonPath("$.total_elements", is(1)))
            .andExpect(jsonPath("$.page", is(0)))
            .andExpect(jsonPath("$.size", is(1)));
    }

    @Test
    void getAllTagsPageableAppliesServerSidePaginationAndSorting() throws Exception {
        jdbcTemplate.update("INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)", "zulu", "z",
                "z", "z", "z", "z");
        jdbcTemplate.update("INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)", "alpha", "a",
                "a", "a", "a", "a");

        doPost("/tags/paged", """
                {"page":0,"size":1,"sort_by":"tag_name","direction":"ASC"}
                """).andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].tag_name", is("alpha")))
            .andExpect(jsonPath("$.total_elements", is(2)))
            .andExpect(jsonPath("$.total_pages", is(2)));
    }

    @Test
    void getAllTagsPageableAllowsAuthenticatedNonAdminUser() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/tags/paged")
            .with(SecurityMockMvcRequestPostProcessors.user("user")
                .roles("USER"))
            .with(SecurityMockMvcRequestPostProcessors.csrf())
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("""
                    {"page":0,"size":10,"sort_by":"tag_name","direction":"ASC"}
                    """))
            .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // GET /api/tags
    // -----------------------------------------------------------------------

    @Test
    void getAllTagsReturnsEmptyListWhenNoTagsExist() throws Exception {
        doGet("/tags").andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getAllTagsReturnsListWithTagsWhenTagsExist() throws Exception {
        jdbcTemplate.update("INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)", "animal",
                "Tier", "animal", "animal", "eläin", "djur");
        jdbcTemplate.update("INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)", "landscape",
                "Landschaft", "landscape", "paisaje", "maisema", "landskap");

        doGet("/tags").andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    // -----------------------------------------------------------------------
    // GET /api/tags/{id}
    // -----------------------------------------------------------------------

    @Test
    void getTagByIdReturns200WhenTagExists() throws Exception {
        jdbcTemplate.update("INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)", "flower",
                "Blume", "flower", "flor", "kukka", "blomma");
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM tags", Long.class);

        doGet("/tags/" + id).andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(id.intValue())))
            .andExpect(jsonPath("$.tag_name", is("flower")));
    }

    @Test
    void getTagByIdReturns400WhenTagNotFound() throws Exception {
        // TagService throws IllegalArgumentException("Tag not found") → GlobalExceptionHandler → 400
        doGet("/tags/99999").andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // POST /api/tags  (create)
    // -----------------------------------------------------------------------

    @Test
    void createTagReturns200WhenRequestIsValid() throws Exception {
        doPost("/tags", VALID_TAG_BODY).andExpect(status().isOk())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.tag_name", is("nature")));
    }

    @Test
    void createTagReturns400WhenTagNameIsBlank() throws Exception {
        // tag_name is @NotBlank
        doPost("/tags", "{\"tag_name\":\"\",\"tag_name_de\":\"d\",\"tag_name_en\":\"e\",\"tag_name_es\":\"e\",\"tag_name_fi\":\"f\",\"tag_name_sv\":\"s\"}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void createTagReturns400WhenRequiredFieldsMissing() throws Exception {
        // Missing tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv
        doPost("/tags", "{\"tag_name\":\"test\"}").andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // PUT /api/tags  (update)
    // -----------------------------------------------------------------------

    @Test
    void updateTagReturns200WhenTagExistsAndRequestIsValid() throws Exception {
        jdbcTemplate.update("INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)", "sky",
                "Himmel", "sky", "cielo", "taivas", "himmel");
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

        doPut("/tags", updateBody).andExpect(status().isOk())
            .andExpect(jsonPath("$.tag_name", is("blue-sky")));
    }

    @Test
    void updateTagReturns400WhenIdIsNullInRequest() throws Exception {
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
        doPut("/tags", body).andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // DELETE /api/tags/{id}
    // -----------------------------------------------------------------------

    @Test
    void deleteTagReturns204WhenTagExists() throws Exception {
        jdbcTemplate.update("INSERT INTO tags (tag_name, tag_name_de, tag_name_en, tag_name_es, tag_name_fi, tag_name_sv) VALUES (?,?,?,?,?,?)", "delete-me",
                "Löschen", "delete", "eliminar", "poista", "radera");
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM tags", Long.class);

        doDelete("/tags/" + id).andExpect(status().isNoContent());
    }

    @Test
    void deleteTagReturns204WhenTagDoesNotExist() throws Exception {
        // deleteById does not throw if entity is missing in JPA default impl
        doDelete("/tags/99999").andExpect(status().isNoContent());
    }

    @Test
    void renameTagAcrossAllRenamesExistingTagWithoutCreatingDuplicate(@TempDir Path tempDirectory) throws Exception {
        var image = tempDirectory.resolve("rename.jpg")
            .toFile();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", image);
        TestExifTool.service()
            .writeSubjects(image, java.util.List.of("I maailmansota"));

        var relativeDirectory = "/" + tempDirectory.getFileName();
        seedFileRow(9001L, "IMAGE", "image/jpeg", image.getName(), relativeDirectory);
        jdbcTemplate.update("INSERT INTO image_files(id, width, height, color_depth, dpi, group_label) VALUES (?,?,?,?,?,?)", 9001L, 8, 8, 24, 72, "");
        jdbcTemplate.update("INSERT INTO tags (tag_name) VALUES (?)", "I maailmansota");
        var oldTagId = jdbcTemplate.queryForObject("SELECT id FROM tags WHERE tag_name = ?", Long.class, "I maailmansota");
        jdbcTemplate.update("INSERT INTO file_tags(file_id, tag_id) VALUES (?,?)", 9001L, oldTagId);

        try {
            var principal = new UserDetailsImpl(1L, "admin", "Admin", "admin@nohost.nodomain", "Disabled", java.util.Set.of(),
                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
            var result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/tags/all/rename")
                .with(SecurityMockMvcRequestPostProcessors.user(principal))
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                        {"tag_name":"I maailmansota","replacement_tag_name":"ensimmäinen maailmansota","file_ids":[]}
                        """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("TAG_RENAME_ACROSS_ALL"))
                .andExpect(jsonPath("$.total_steps").value(1))
                .andReturn();
            awaitTaskCompletion(result);

            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tags", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tags WHERE tag_name = ?", Integer.class, "ensimmäinen maailmansota")).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_tags ft JOIN tags t ON t.id = ft.tag_id WHERE ft.file_id = ? AND t.tag_name = ?",
                    Integer.class, 9001L, "ensimmäinen maailmansota"))
                .isEqualTo(1);
        } finally {
            deleteFileRow(9001L);
        }
    }

    /**
     * Waits until the background task acknowledged by {@code result} has finished and asserts that it completed.
     */
    private void awaitTaskCompletion(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        var taskId = tools.jackson.databind.json.JsonMapper.shared()
            .readTree(result.getResponse()
                .getContentAsString())
            .get("task_id")
            .asString();
        var task = taskProgressStore.find(taskId)
            .orElseThrow();
        var deadline = java.time.Instant.now()
            .plusSeconds(15);
        while (!task.isFinished()
               && java.time.Instant.now()
                   .isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertThat(task.getStatus()).as(task.getErrorMessage())
            .isEqualTo(fi.poltsi.vempain.common.api.TaskStatusEnum.COMPLETED);
    }

    private void seedTaggedImage(long fileId, java.io.File image, String relativeDirectory, Long aclUser, boolean modify, String... tagNames) {
        seedFileRowWithAcl(fileId, "IMAGE", "image/jpeg", image.getName(), relativeDirectory, aclUser, true, modify, modify);
        jdbcTemplate.update("INSERT INTO image_files(id, width, height, color_depth, dpi, group_label) VALUES (?, 16, 16, 24, 72, '')", fileId);
        for (var tagName : tagNames) {
            jdbcTemplate.update("INSERT INTO tags (tag_name) VALUES (?) ON CONFLICT (tag_name) DO NOTHING", tagName);
            var tagId = jdbcTemplate.queryForObject("SELECT id FROM tags WHERE tag_name = ?", Long.class, tagName);
            jdbcTemplate.update("INSERT INTO file_tags(file_id, tag_id) VALUES (?, ?)", fileId, tagId);
        }
    }

    private List<String> subjectsOf(java.io.File image) throws Exception {
        return MetadataJson.extractSubjects(TestExifTool.service()
            .readMetadata(image));
    }

    @Test
    void addReplaceRenameAndRemoveTagsOnSelectedFiles(@TempDir Path tempDirectory) throws Exception {
        var image = SampleFiles.jpeg(tempDirectory.resolve("tagged.jpg"));
        TestExifTool.service()
            .writeSubjects(image, List.of("ctc-old"));
        seedTaggedImage(9101L, image, "/" + tempDirectory.getFileName(), 1L, true, "ctc-old");
        try {
            doPost("/tags/files/add", """
                    {"tag_name":"ctc-new","tag_name_fi":"uusi","file_ids":[9101]}
                    """).andExpect(status().isNoContent());
            assertThat(subjectsOf(image)).containsExactly("ctc-old", "ctc-new");
            assertThat(jdbcTemplate.queryForObject("SELECT tag_name_fi FROM tags WHERE tag_name = ?", String.class, "ctc-new")).isEqualTo("uusi");

            doPost("/tags/files/replace", """
                    {"tag_name":"ctc-new","replacement_tag_name":"ctc-old","file_ids":[9101]}
                    """).andExpect(status().isNoContent());
            assertThat(subjectsOf(image)).containsExactly("ctc-old");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tags WHERE tag_name = ?", Integer.class, "ctc-new")).as("the unused tag is removed")
                .isZero();

            doPost("/tags/files/rename", """
                    {"tag_name":"ctc-old","replacement_tag_name":"ctc-renamed","file_ids":[9101]}
                    """).andExpect(status().isNoContent());
            assertThat(subjectsOf(image)).containsExactly("ctc-renamed");

            doPost("/tags/files/remove", """
                    {"tag_name":"ctc-renamed","file_ids":[9101]}
                    """).andExpect(status().isNoContent());
            assertThat(subjectsOf(image)).isEmpty();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_tags WHERE file_id = ?", Integer.class, 9101L)).isZero();
        } finally {
            deleteFileRow(9101L);
        }
    }

    @Test
    void tagMutationsAreDeniedWithoutTheModifyPrivilege(@TempDir Path tempDirectory) throws Exception {
        var image = SampleFiles.jpeg(tempDirectory.resolve("locked.jpg"));
        TestExifTool.service()
            .writeSubjects(image, List.of("ctc-locked"));
        seedTaggedImage(9102L, image, "/" + tempDirectory.getFileName(), 1L, false, "ctc-locked");
        try {
            doPost("/tags/files/add", "{\"tag_name\":\"ctc-new\",\"file_ids\":[9102]}").andExpect(status().isForbidden());
            doPost("/tags/files/remove", "{\"tag_name\":\"ctc-locked\",\"file_ids\":[9102]}").andExpect(status().isForbidden());
            doPost("/tags/all/remove", "{\"tag_name\":\"ctc-locked\"}").andExpect(status().isForbidden());
            assertThat(subjectsOf(image)).containsExactly("ctc-locked");
        } finally {
            deleteFileRow(9102L);
        }
    }

    @Test
    void replaceAndRemoveAcrossAllFilesRunAsTasks(@TempDir Path tempDirectory) throws Exception {
        var image = SampleFiles.jpeg(tempDirectory.resolve("everywhere.jpg"));
        TestExifTool.service()
            .writeSubjects(image, List.of("ctc-all", "ctc-keep"));
        seedTaggedImage(9103L, image, "/" + tempDirectory.getFileName(), 1L, true, "ctc-all", "ctc-keep");
        try {
            var replaced = doPost("/tags/all/replace", """
                    {"tag_name":"ctc-all","replacement_tag_name":"ctc-keep"}
                    """).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("TAG_REPLACE_ACROSS_ALL"))
                .andExpect(jsonPath("$.total_steps").value(1))
                .andReturn();
            awaitTaskCompletion(replaced);
            assertThat(subjectsOf(image)).containsExactly("ctc-keep");

            var removed = doPost("/tags/all/remove", """
                    {"tag_name":"ctc-keep"}
                    """).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type").value("TAG_REMOVE_FROM_ALL"))
                .andReturn();
            awaitTaskCompletion(removed);
            assertThat(subjectsOf(image)).isEmpty();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tags WHERE tag_name IN (?, ?)", Integer.class, "ctc-all", "ctc-keep")).isZero();
        } finally {
            deleteFileRow(9103L);
        }
    }

    @Test
    void filesByTagListOnlyTheReadableTaggedFiles(@TempDir Path tempDirectory) throws Exception {
        var readable = SampleFiles.jpeg(tempDirectory.resolve("readable.jpg"));
        var hidden = SampleFiles.jpeg(tempDirectory.resolve("hidden.jpg"));
        seedTaggedImage(9104L, readable, "/" + tempDirectory.getFileName(), 1L, true, "ctc-listed");
        seedTaggedImage(9105L, hidden, "/" + tempDirectory.getFileName(), 2L, true, "ctc-listed");
        var tagId = jdbcTemplate.queryForObject("SELECT id FROM tags WHERE tag_name = ?", Long.class, "ctc-listed");
        try {
            doPost("/tags/" + tagId + "/files/paged", """
                    {"page":0,"size":10,"search":"readable","sort_by":"filename","direction":"ASC"}
                    """).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(9104)))
                .andExpect(jsonPath("$.total_elements", is(1)));
            doPost("/tags/" + tagId + "/files/paged", "{\"page\":0,\"size\":10}").andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        } finally {
            deleteFileRow(9104L);
            deleteFileRow(9105L);
        }
    }
}
