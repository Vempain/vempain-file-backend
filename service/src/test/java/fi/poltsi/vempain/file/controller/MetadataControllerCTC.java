package fi.poltsi.vempain.file.controller;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Instant;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import fi.poltsi.vempain.common.api.TaskStatusEnum;
import fi.poltsi.vempain.common.task.TaskProgressStore;
import fi.poltsi.vempain.file.tools.TestExifTool;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link MetadataController}: the metadata summary list, the ACL-filtered file list of one value and the background rewrite of
 * a value (end to end against a real JPEG with ExifTool).
 */
@SuppressWarnings("checkstyle:OverloadMethodsDeclarationOrder")
class MetadataControllerCTC extends AbstractControllerCTC {

    private static final long READABLE_IMAGE = 9101L;
    private static final long READABLE_DOCUMENT = 9102L;
    private static final long HIDDEN_IMAGE = 9103L;
    private static final long LOCKED_IMAGE = 9104L;
    private static final long REWRITTEN_IMAGE = 9105L;
    private static final String VALUE = "ctc-meta-jane";
    private static final String OTHER_VALUE = "ctc-meta-john";
    private static final String LOCKED_VALUE = "ctc-meta-locked";

    @Autowired
    private TaskProgressStore taskProgressStore;

    @BeforeEach
    void seedMetadata() {
        seedFileRow(READABLE_IMAGE, "IMAGE", "image/jpeg", "ctc-meta-a.jpg", "/ctc/meta");
        seedFileRow(READABLE_DOCUMENT, "DOCUMENT", "application/pdf", "ctc-meta-b.pdf", "/ctc/meta");
        // Readable by user 2 only: never listed for the CTC principal (user 1)
        seedFileRowWithAcl(HIDDEN_IMAGE, "IMAGE", "image/jpeg", "ctc-meta-c.jpg", "/ctc/meta", 2L, true, true, true);
        // User 1 may read but not modify
        seedFileRowWithAcl(LOCKED_IMAGE, "IMAGE", "image/jpeg", "ctc-meta-d.jpg", "/ctc/meta", 1L, true, false, false);
        jdbcTemplate.update("UPDATE files SET filesize = 4096 WHERE id = ?", READABLE_DOCUMENT);
        // JOINED inheritance: every file row needs its subtype row to be loadable as an entity
        for (var id : new long[]{ READABLE_IMAGE, HIDDEN_IMAGE, LOCKED_IMAGE }) {
            jdbcTemplate.update("INSERT INTO image_files(id, width, height, color_depth, dpi, group_label) VALUES (?, 8, 8, 24, 72, '')", id);
        }
        jdbcTemplate.update("INSERT INTO document_files(id, page_count, format) VALUES (?, 1, 'PDF')", READABLE_DOCUMENT);

        seedMetadata(READABLE_IMAGE, "XMP-dc", "Creator", VALUE);
        seedMetadata(READABLE_DOCUMENT, "XMP-dc", "Creator", VALUE);
        seedMetadata(HIDDEN_IMAGE, "XMP-dc", "Creator", VALUE);
        seedMetadata(READABLE_DOCUMENT, "XMP-dc", "Creator", OTHER_VALUE);
        seedMetadata(READABLE_IMAGE, "Composite", "ImageSize", "ctc-meta-8x8");
        seedMetadata(LOCKED_IMAGE, "XMP-dc", "Creator", LOCKED_VALUE);
    }

    @AfterEach
    void cleanUp() {
        for (var id : new long[]{ READABLE_IMAGE, READABLE_DOCUMENT, HIDDEN_IMAGE, LOCKED_IMAGE, REWRITTEN_IMAGE }) {
            // metadata rows cascade with the file row
            deleteFileRow(id);
        }
    }

    // -----------------------------------------------------------------------
    // POST /api/metadata/paged
    // -----------------------------------------------------------------------

    @Test
    void getMetadataPageableGroupsByKeyAndValueCountsEveryFileAndFiltersByKeyAndValue() throws Exception {
        doPost("/metadata/paged", """
                {"page":0,"size":10,"metadata_key":"creator","metadata_value":"ctc-meta-j","sort_by":"file_count","direction":"DESC"}
                """).andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.total_elements", is(2)))
            .andExpect(jsonPath("$.content[0].metadata_group", is("XMP-dc")))
            .andExpect(jsonPath("$.content[0].metadata_key", is("Creator")))
            .andExpect(jsonPath("$.content[0].metadata_value", is(VALUE)))
            // metadata has no ACL: the hidden file is counted too
            .andExpect(jsonPath("$.content[0].file_count", is(3)))
            .andExpect(jsonPath("$.content[0].writable", is(true)))
            .andExpect(jsonPath("$.content[1].metadata_value", is(OTHER_VALUE)))
            .andExpect(jsonPath("$.content[1].file_count", is(1)));
    }

    @Test
    void getMetadataPageableFlagsReadOnlyGroupsAndTreatsWildcardsAsText() throws Exception {
        doPost("/metadata/paged", """
                {"page":0,"size":10,"metadata_group":"Composite","metadata_value":"ctc-meta-"}
                """).andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].metadata_key", is("ImageSize")))
            .andExpect(jsonPath("$.content[0].writable", is(false)));

        doPost("/metadata/paged", """
                {"page":0,"size":10,"metadata_value":"ctc-meta-%"}
                """).andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void getMetadataPageableRejectsInvalidPaging() throws Exception {
        doPost("/metadata/paged", """
                {"page":0,"size":0}
                """).andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // POST /api/metadata/files/paged
    // -----------------------------------------------------------------------

    @Test
    void getFilesByMetadataListsOnlyReadableFilesSortedAsRequested() throws Exception {
        doPost("/metadata/files/paged", """
                {"metadata_group":"XMP-dc","metadata_key":"Creator","metadata_value":"%s","page":0,"size":10,"sort_by":"filesize","direction":"DESC"}
                """.formatted(VALUE)).andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.total_elements", is(2)))
            .andExpect(jsonPath("$.content[0].id", is((int) READABLE_DOCUMENT)))
            .andExpect(jsonPath("$.content[1].id", is((int) READABLE_IMAGE)));
    }

    @Test
    void getFilesByMetadataFiltersByFileTypeAndFilename() throws Exception {
        doPost("/metadata/files/paged", """
                {"metadata_group":"XMP-dc","metadata_key":"Creator","metadata_value":"%s","page":0,"size":10,"file_type":"DOCUMENT"}
                """.formatted(VALUE)).andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].file_type", is("DOCUMENT")));

        doPost("/metadata/files/paged", """
                {"metadata_group":"XMP-dc","metadata_key":"Creator","metadata_value":"%s","page":0,"size":10,"filename":"META-A"}
                """.formatted(VALUE)).andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].filename", is("ctc-meta-a.jpg")));
    }

    @Test
    void getFilesByMetadataRequiresTheMetadataSelector() throws Exception {
        doPost("/metadata/files/paged", """
                {"metadata_group":"XMP-dc","page":0,"size":10}
                """).andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // POST /api/metadata/value
    // -----------------------------------------------------------------------

    @Test
    void updateMetadataValueRejectsReadOnlyGroupsAndUnknownValues() throws Exception {
        doPost("/metadata/value", """
                {"metadata_group":"Composite","metadata_key":"ImageSize","metadata_value":"ctc-meta-8x8","new_value":"9x9"}
                """).andExpect(status().isBadRequest());

        doPost("/metadata/value", """
                {"metadata_group":"XMP-dc","metadata_key":"Creator","metadata_value":"ctc-meta-nobody","new_value":"x"}
                """).andExpect(status().isBadRequest());

        doPost("/metadata/value", """
                {"metadata_group":"XMP-dc","metadata_key":"","metadata_value":"x","new_value":"y"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void updateMetadataValueReturns403WhenAFileMayNotBeModified() throws Exception {
        doPost("/metadata/value", """
                {"metadata_group":"XMP-dc","metadata_key":"Creator","metadata_value":"%s","new_value":"x"}
                """.formatted(LOCKED_VALUE)).andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_record WHERE task_type = 'METADATA_UPDATE_VALUE' AND title LIKE ?", Integer.class,
                "%" + LOCKED_VALUE + "%"))
            .isZero();
    }

    @Test
    void updateMetadataValueRewritesTheFileRefreshesTheRowAndRemovesTheEntryWhenEmpty(@TempDir Path tempDirectory) throws Exception {
        var image = tempDirectory.resolve("ctc-meta-rewrite.jpg")
            .toFile();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", image);
        TestExifTool.service()
            .writeValue(image, "XMP-dc", "Creator", "ctc-meta-old");

        var relativeDirectory = "/" + tempDirectory.getFileName();
        seedFileRow(REWRITTEN_IMAGE, "IMAGE", "image/jpeg", image.getName(), relativeDirectory);
        jdbcTemplate.update("INSERT INTO image_files(id, width, height, color_depth, dpi, group_label) VALUES (?,?,?,?,?,?)", REWRITTEN_IMAGE, 8, 8, 24, 72,
                "");
        seedMetadata(REWRITTEN_IMAGE, "XMP-dc", "Creator", "ctc-meta-old");

        var accepted = doPost("/metadata/value", """
                {"metadata_group":"XMP-dc","metadata_key":"Creator","metadata_value":"ctc-meta-old","new_value":"ctc-meta-new"}
                """).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.type", is("METADATA_UPDATE_VALUE")))
            .andExpect(jsonPath("$.total_steps", is(1)))
            .andReturn();
        awaitTaskCompletion(accepted);

        assertThat(TestExifTool.service()
            .readMetadata(image)
            .getJSONObject("XMP-dc")
            .getString("Creator")).isEqualTo("ctc-meta-new");
        assertThat(jdbcTemplate.queryForList("SELECT metadata_value FROM metadata WHERE file_id = ? AND metadata_group = 'XMP-dc' AND metadata_key = 'Creator'",
                String.class, REWRITTEN_IMAGE))
            .containsExactly("ctc-meta-new");
        assertThat(jdbcTemplate.queryForObject("SELECT modifier FROM files WHERE id = ?", Long.class, REWRITTEN_IMAGE)).isEqualTo(1L);

        var removed = doPost("/metadata/value", """
                {"metadata_group":"XMP-dc","metadata_key":"Creator","metadata_value":"ctc-meta-new","new_value":""}
                """).andExpect(status().isAccepted())
            .andReturn();
        awaitTaskCompletion(removed);

        assertThat(TestExifTool.service()
            .readMetadata(image)
            .optJSONObject("XMP-dc")).satisfiesAnyOf(group -> assertThat(group).isNull(), group -> assertThat(group.has("Creator")).isFalse());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM metadata WHERE file_id = ? AND metadata_group = 'XMP-dc' AND metadata_key = 'Creator'",
                Integer.class, REWRITTEN_IMAGE))
            .isZero();
    }

    private void seedMetadata(long fileId, String group, String key, String value) {
        jdbcTemplate.update("INSERT INTO metadata (file_id, metadata_group, metadata_key, metadata_value) VALUES (?,?,?,?)", fileId, group, key, value);
    }

    /**
     * Waits until the background task acknowledged by {@code result} has finished and asserts that it completed.
     */
    private void awaitTaskCompletion(MvcResult result) throws Exception {
        var taskId = JsonMapper.shared()
            .readTree(result.getResponse()
                .getContentAsString())
            .get("task_id")
            .asString();
        var task = taskProgressStore.find(taskId)
            .orElseThrow();
        var deadline = Instant.now()
            .plusSeconds(15);
        while (!task.isFinished()
               && Instant.now()
                   .isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertThat(task.getStatus()).as(task.getErrorMessage())
            .isEqualTo(TaskStatusEnum.COMPLETED);
    }
}
