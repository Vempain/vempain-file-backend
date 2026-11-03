package fi.poltsi.vempain.file.api;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import fi.poltsi.vempain.file.api.request.CreateGpsTimeSeriesRequest;
import fi.poltsi.vempain.file.api.request.PublishAclRequest;
import fi.poltsi.vempain.file.api.request.PublishMusicDatasetRequest;
import fi.poltsi.vempain.file.api.response.PublishDataSetStatusResponse;
import fi.poltsi.vempain.file.api.response.PublishedAclResponse;
import fi.poltsi.vempain.file.rest.DataPublishAPI;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON contract of the data set creation requests: the optional {@code publish_to_site} flag of both.
 */
class DataPublishContractJTC {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pathRemainsStable() {
        assertEquals("/data-publish", DataPublishAPI.BASE_PATH);
    }

    @Test
    void requestsAreSnakeCaseAndTheFlagIsOptional() throws Exception {
        var gps = objectMapper.readValue("{\"file_group_id\": 7, \"time_series_name\": \"trip\", \"publish_to_site\": true}", CreateGpsTimeSeriesRequest.class);
        assertEquals(7L, gps.getFileGroupId());
        assertEquals("trip", gps.getTimeSeriesName());
        assertTrue(gps.getPublishToSite());
        assertNull(objectMapper.readValue("{\"file_group_id\": 7, \"time_series_name\": \"trip\"}", CreateGpsTimeSeriesRequest.class)
            .getPublishToSite());
        assertEquals(Set.of("file_group_id", "time_series_name", "publish_to_site", "acls"),
                new HashSet<>(objectMapper.readTree(objectMapper.writeValueAsString(gps))
                    .propertyNames()));

        var music = objectMapper.readValue("{\"publish_to_site\": true}", PublishMusicDatasetRequest.class);
        assertTrue(music.getPublishToSite());
        assertNull(objectMapper.readValue("{}", PublishMusicDatasetRequest.class)
            .getPublishToSite());
        assertEquals(Set.of("publish_to_site", "acls"), new HashSet<>(objectMapper.readTree(objectMapper.writeValueAsString(music))
            .propertyNames()));
    }

    @Test
    void requestsCarryTheGranteesInSnakeCaseAndTheStatusAnswersThemBack() throws Exception {
        var json = "{\"publish_to_site\": false, \"acls\": [{\"user_id\": 12, \"unit_id\": null, \"read_privilege\": true, \"create_privilege\": false, "
                + "\"modify_privilege\": true, \"delete_privilege\": false}]}";
        var music = objectMapper.readValue(json, PublishMusicDatasetRequest.class);
        assertEquals(1, music.getAcls()
            .size());
        var acl = music.getAcls()
            .get(0);
        assertEquals(12L, acl.getUserId());
        assertNull(acl.getUnitId());
        assertTrue(acl.isReadPrivilege());
        assertTrue(acl.isModifyPrivilege());
        assertTrue(acl.isUserOrUnit());
        // without the list the grantees of an existing data set stay as they are
        assertNull(objectMapper.readValue("{}", PublishMusicDatasetRequest.class)
            .getAcls());

        var gps = objectMapper.readValue("{\"file_group_id\": 7, \"time_series_name\": \"trip\", \"acls\": []}", CreateGpsTimeSeriesRequest.class);
        assertTrue(gps.getAcls()
            .isEmpty(), "an empty list removes every additional grantee");

        var status = PublishDataSetStatusResponse.builder()
            .identifier("music_library")
            .published(true)
            .acls(java.util.List.of(PublishedAclResponse.builder()
                .unitId(3L)
                .readPrivilege(true)
                .build()))
            .build();
        var tree = objectMapper.readTree(objectMapper.writeValueAsString(status));
        assertEquals(Set.of("identifier", "published", "acls"), new HashSet<>(tree.propertyNames()));
        assertEquals(3L, tree.get("acls")
            .get(0)
            .get("unit_id")
            .asLong());
        // the status rows have the shape of the request rows, so the dialog can send them back unchanged
        var resent = objectMapper.readValue(tree.get("acls")
            .toString(), PublishAclRequest[].class);
        assertEquals(3L, resent[0].getUnitId());
        assertTrue(resent[0].isReadPrivilege());
    }

    @Test
    void pagedRequestAndRowAreSnakeCase() throws Exception {
        var request = objectMapper.readValue(
                "{\"page\": 1, \"size\": 25, \"sort_by\": \"published\", \"direction\": \"DESC\", \"filter_column\": \"type\", \"type\": \"time_series\", "
                        + "\"modified_after_publish\": true}",
                fi.poltsi.vempain.file.api.request.DataSetPagedRequest.class);
        assertEquals(1, request.getPage());
        assertEquals("published", request.getSortBy());
        assertEquals("type", request.getFilterColumn());
        assertEquals("time_series", request.getType());
        assertTrue(request.getModifiedAfterPublish());

        var row = fi.poltsi.vempain.file.api.response.DataSetSummaryResponse.builder()
            .id(5L)
            .aclId(42L)
            .identifier("music_library")
            .type("tabulated")
            .build();
        assertEquals(Set.of("id", "acl_id", "identifier", "type", "description", "generated", "creator", "created", "modifier", "modified", "published"),
                new HashSet<>(objectMapper.readTree(objectMapper.writeValueAsString(row))
                    .propertyNames()));
    }
}
