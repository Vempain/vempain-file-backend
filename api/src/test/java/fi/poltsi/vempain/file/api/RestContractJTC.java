package fi.poltsi.vempain.file.api;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import fi.poltsi.vempain.file.VempainServiceApiDefinition;
import fi.poltsi.vempain.file.api.request.PublishAclRequest;
import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.rest.DataPublishAPI;
import fi.poltsi.vempain.file.rest.FileContentAPI;
import fi.poltsi.vempain.file.rest.FileGroupAPI;
import fi.poltsi.vempain.file.rest.FileScannerAPI;
import fi.poltsi.vempain.file.rest.LocationAPI;
import fi.poltsi.vempain.file.rest.MetadataAPI;
import fi.poltsi.vempain.file.rest.PathCompletionAPI;
import fi.poltsi.vempain.file.rest.PublishAPI;
import fi.poltsi.vempain.file.rest.StatisticsAPI;
import fi.poltsi.vempain.file.rest.TagAPI;
import fi.poltsi.vempain.file.rest.files.ArchiveFileAPI;
import fi.poltsi.vempain.file.rest.files.AudioFileAPI;
import fi.poltsi.vempain.file.rest.files.BinaryFileAPI;
import fi.poltsi.vempain.file.rest.files.DataFileAPI;
import fi.poltsi.vempain.file.rest.files.DocumentFileAPI;
import fi.poltsi.vempain.file.rest.files.ExecutableFileAPI;
import fi.poltsi.vempain.file.rest.files.FontFileAPI;
import fi.poltsi.vempain.file.rest.files.IconFileAPI;
import fi.poltsi.vempain.file.rest.files.ImageFileAPI;
import fi.poltsi.vempain.file.rest.files.InteractiveFileAPI;
import fi.poltsi.vempain.file.rest.files.MusicFileAPI;
import fi.poltsi.vempain.file.rest.files.ThumbFileAPI;
import fi.poltsi.vempain.file.rest.files.VectorFileAPI;
import fi.poltsi.vempain.file.rest.files.VideoFileAPI;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the published REST paths and the hand-written validation rules of the request DTOs.
 */
class RestContractJTC {

    private final JsonMapper mapper = JsonMapper.builder()
        .build();

    @Test
    void basePathsRemainStable() {
        assertEquals("/files", FileContentAPI.BASE_PATH);
        assertEquals("/scan-files", FileScannerAPI.BASE_PATH);
        assertEquals("/location", LocationAPI.BASE_PATH);
        assertEquals("/location/guard", LocationAPI.GUARD_PATH);
        assertEquals("/path-completion", PathCompletionAPI.BASE_PATH);
        assertEquals("/metadata", MetadataAPI.BASE_PATH);
        assertEquals("/data-publish", DataPublishAPI.BASE_PATH);
        assertEquals("/tags", TagAPI.BASE_PATH);
        assertEquals("/publish", PublishAPI.BASE_PATH);
        assertEquals("/file-groups", FileGroupAPI.BASE_PATH);
        assertEquals("/statistics", StatisticsAPI.BASE_PATH);
        assertEquals("/files/archive", ArchiveFileAPI.BASE_PATH);
        assertEquals("/files/audio", AudioFileAPI.BASE_PATH);
        assertEquals("/files/binary", BinaryFileAPI.BASE_PATH);
        assertEquals("/files/data", DataFileAPI.BASE_PATH);
        assertEquals("/files/document", DocumentFileAPI.BASE_PATH);
        assertEquals("/files/executable", ExecutableFileAPI.BASE_PATH);
        assertEquals("/files/font", FontFileAPI.BASE_PATH);
        assertEquals("/files/icon", IconFileAPI.BASE_PATH);
        assertEquals("/files/image", ImageFileAPI.BASE_PATH);
        assertEquals("/files/interactive", InteractiveFileAPI.BASE_PATH);
        assertEquals("/files/music", MusicFileAPI.BASE_PATH);
        assertEquals("/files/thumb", ThumbFileAPI.BASE_PATH);
        assertEquals("/files/vector", VectorFileAPI.BASE_PATH);
        assertEquals("/files/video", VideoFileAPI.BASE_PATH);
    }

    @Test
    void apiDefinitionIsAnOpenApiUtilityClass() throws Exception {
        assertNotNull(VempainServiceApiDefinition.class.getAnnotation(OpenAPIDefinition.class));
        assertEquals("Vempain File Service REST endpoints", VempainServiceApiDefinition.class.getAnnotation(OpenAPIDefinition.class)
            .info()
            .title());
        var constructor = VempainServiceApiDefinition.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    }

    @Test
    void publishAclRequestNeedsAtLeastOnePrivilegeAndHidesTheCheckFromJson() throws Exception {
        assertFalse(PublishAclRequest.builder()
            .userId(1L)
            .build()
            .isAnyPrivilege());
        assertTrue(PublishAclRequest.builder()
            .userId(1L)
            .readPrivilege(true)
            .build()
            .isAnyPrivilege());
        assertTrue(PublishAclRequest.builder()
            .userId(1L)
            .createPrivilege(true)
            .build()
            .isAnyPrivilege());
        assertTrue(PublishAclRequest.builder()
            .userId(1L)
            .modifyPrivilege(true)
            .build()
            .isAnyPrivilege());
        assertTrue(PublishAclRequest.builder()
            .userId(1L)
            .deletePrivilege(true)
            .build()
            .isAnyPrivilege());

        var json = mapper.writeValueAsString(PublishAclRequest.builder()
            .userId(12L)
            .modifyPrivilege(true)
            .build());
        assertTrue(json.contains("\"user_id\":12"), json);
        assertTrue(json.contains("\"modify_privilege\":true"), json);
        assertFalse(json.contains("any_privilege"), json);

        var read = mapper.readValue("{\"user_id\":3,\"read_privilege\":true,\"create_privilege\":false,\"modify_privilege\":false,\"delete_privilege\":false}",
                PublishAclRequest.class);
        assertEquals(3L, read.getUserId());
        assertTrue(read.isReadPrivilege());
    }

    @Test
    void publishAclRequestNamesExactlyOneOfUserAndUnit() throws Exception {
        var unit = mapper.readValue("{\"unit_id\":3,\"read_privilege\":true}", PublishAclRequest.class);
        assertEquals(3L, unit.getUnitId());
        assertEquals(null, unit.getUserId());
        assertTrue(unit.isUserOrUnit());
        assertFalse(mapper.writeValueAsString(unit)
            .contains("user_or_unit"), "the derived flag stays out of the JSON");

        assertFalse(PublishAclRequest.builder()
            .userId(1L)
            .unitId(3L)
            .readPrivilege(true)
            .build()
            .isUserOrUnit());
        assertFalse(PublishAclRequest.builder()
            .readPrivilege(true)
            .build()
            .isUserOrUnit());
    }

    @Test
    void scanRequestNeedsAtLeastOneDirectory() throws Exception {
        assertFalse(new ScanRequest().isDirectoryValid());
        assertTrue(new ScanRequest("/originals", null).isDirectoryValid());
        assertTrue(new ScanRequest(null, "/exports").isDirectoryValid());
        assertTrue(new ScanRequest("/originals", "/exports").isDirectoryValid());

        var read = mapper.readValue("{\"original_directory\":\"/a\",\"export_directory\":null}", ScanRequest.class);
        assertEquals("/a", read.getOriginalDirectory());
        assertTrue(read.isDirectoryValid());
        assertTrue(mapper.writeValueAsString(read)
            .contains("\"original_directory\":\"/a\""));
    }

    @Test
    void geoCoordinateCarriesDecimalDegrees() throws Exception {
        var coordinate = GeoCoordinate.builder()
            .latitude(new BigDecimal("60.16952"))
            .longitude(new BigDecimal("24.93545"))
            .build();
        var json = mapper.writeValueAsString(coordinate);
        assertTrue(json.contains("\"latitude\":60.16952"), json);
        assertEquals(coordinate, mapper.readValue(json, GeoCoordinate.class));
    }
}
