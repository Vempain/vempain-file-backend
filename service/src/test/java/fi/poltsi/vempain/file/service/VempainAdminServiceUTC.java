package fi.poltsi.vempain.file.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.admin.api.request.file.SiteFilePagedRequest;
import fi.poltsi.vempain.admin.api.response.file.FileIngestAclResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestGalleryAclResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestSiteFileAclResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestUnitResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestUserResponse;
import fi.poltsi.vempain.admin.api.response.file.SiteFileResponse;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.file.feign.VempainAdminFileClient;
import fi.poltsi.vempain.file.feign.VempainAdminFileIngestClient;
import fi.poltsi.vempain.file.metrics.FileMetrics;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (UTC) for {@link VempainAdminService}.
 */
@ExtendWith(MockitoExtension.class)
class VempainAdminServiceUTC {

    @Mock
    private VempainAdminFileIngestClient vempainAdminFileIngestClient;
    @Mock
    private VempainAdminFileClient vempainAdminFileClient;
    @Mock
    private fi.poltsi.vempain.file.feign.VempainAdminDataClient vempainAdminDataClient;
    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FileMetrics fileMetrics;
    @InjectMocks
    private VempainAdminService vempainAdminService;

    private FeignException fakeFeignException(int status) {
        return FeignException.errorStatus("test", feign.Response.builder()
            .status(status)
            .reason("error")
            .request(Request.create(Request.HttpMethod.POST, "http://test", Map.of(), null, new RequestTemplate()))
            .headers(Map.of())
            .build());
    }

    // ------------------------------------------------------------------
    // uploadAsSiteFile
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("uploadAsSiteFile")
    class UploadAsSiteFile {

        @Test
        void successReturnsResponse(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("export.jpg");
            Files.writeString(file, "img-content");

            var request = FileIngestRequest.builder()
                .mimeType("image/jpeg")
                .build();
            var expected = FileIngestResponse.builder()
                .galleryId(99L)
                .siteFileId(42L)
                .updated(false)
                .build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(vempainAdminFileIngestClient.ingest(anyString(), any())).thenReturn(ResponseEntity.ok(expected));

            var result = vempainAdminService.uploadAsSiteFile(file.toFile(), request);
            assertThat(result.getSiteFileId()).isEqualTo(42L);
        }

        @Test
        void nullResponseThrowsAuthException(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("export.jpg");
            Files.writeString(file, "img");
            var request = FileIngestRequest.builder()
                .mimeType("image/jpeg")
                .build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(vempainAdminFileIngestClient.ingest(anyString(), any())).thenReturn(null);

            assertThrows(VempainAuthenticationException.class, () -> vempainAdminService.uploadAsSiteFile(file.toFile(), request));
        }

        @Test
        void nonSuccessStatusThrowsAuthException(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("export.jpg");
            Files.writeString(file, "img");
            var request = FileIngestRequest.builder()
                .mimeType("image/jpeg")
                .build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(vempainAdminFileIngestClient.ingest(anyString(), any())).thenReturn(ResponseEntity.badRequest()
                .build());

            assertThrows(VempainAuthenticationException.class, () -> vempainAdminService.uploadAsSiteFile(file.toFile(), request));
        }

        @Test
        void forbidden403ThrowsAuthException(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("export.jpg");
            Files.writeString(file, "img");
            var request = FileIngestRequest.builder()
                .mimeType("image/jpeg")
                .build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(vempainAdminFileIngestClient.ingest(anyString(), any())).thenThrow(fakeFeignException(403));

            assertThrows(VempainAuthenticationException.class, () -> vempainAdminService.uploadAsSiteFile(file.toFile(), request));
        }

        @Test
        void feignExceptionNonForbiddenRethrows(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("export.jpg");
            Files.writeString(file, "img");
            var request = FileIngestRequest.builder()
                .mimeType("image/jpeg")
                .build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            var feignEx = fakeFeignException(500);
            when(vempainAdminFileIngestClient.ingest(anyString(), any())).thenThrow(feignEx);

            assertThrows(FeignException.class, () -> vempainAdminService.uploadAsSiteFile(file.toFile(), request));
        }
    }

    // ------------------------------------------------------------------
    // getPageableSiteFiles
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("getPageableSiteFiles")
    class GetPageableSiteFiles {

        @Test
        void successReturnsBody() {
            var mockBody = new PagedResponse<SiteFileResponse>();
            when(vempainAdminFileClient.getPageableSiteFiles(any(SiteFilePagedRequest.class))).thenReturn(ResponseEntity.ok(mockBody));

            var result = vempainAdminService.getPageableSiteFiles(FileTypeEnum.IMAGE, 0, 10, "id", Sort.Direction.ASC, null, null);
            assertThat(result).isNotNull();
        }

        @Test
        void nullResponseReturnsNull() {
            when(vempainAdminFileClient.getPageableSiteFiles(any(SiteFilePagedRequest.class))).thenReturn(null);

            var result = vempainAdminService.getPageableSiteFiles(FileTypeEnum.IMAGE, 0, 10, "id", Sort.Direction.ASC, null, null);
            assertNull(result);
        }

        @Test
        void nonSuccessStatusReturnsNull() {
            when(vempainAdminFileClient.getPageableSiteFiles(any(SiteFilePagedRequest.class))).thenReturn(ResponseEntity.badRequest()
                .build());

            var result = vempainAdminService.getPageableSiteFiles(FileTypeEnum.IMAGE, 0, 10, "id", Sort.Direction.ASC, null, null);
            assertNull(result);
        }

        @Test
        void feignExceptionReturnsNull() {
            when(vempainAdminFileClient.getPageableSiteFiles(any(SiteFilePagedRequest.class))).thenThrow(fakeFeignException(503));

            var result = vempainAdminService.getPageableSiteFiles(FileTypeEnum.IMAGE, 0, 10, "id", Sort.Direction.ASC, null, null);
            assertNull(result);
        }
    }

    // ------------------------------------------------------------------
    // listIngestUsers
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("listIngestUsers")
    class ListIngestUsers {

        @Test
        void returnsTheUsersOfTheAdminBackend() {
            var user = FileIngestUserResponse.builder()
                .id(12L)
                .loginName("arnold")
                .name("Arnold")
                .nick("Ahnold")
                .build();
            when(vempainAdminFileIngestClient.listIngestUsers()).thenReturn(ResponseEntity.ok(List.of(user)));

            var users = vempainAdminService.listIngestUsers();

            assertThat(users).containsExactly(user);
        }

        @Test
        void forbiddenTriggersReauthentication() {
            when(vempainAdminFileIngestClient.listIngestUsers()).thenThrow(fakeFeignException(403));

            assertThrows(VempainAuthenticationException.class, () -> vempainAdminService.listIngestUsers());
        }

        @Test
        void otherFailuresBecomeBadGateway() {
            when(vempainAdminFileIngestClient.listIngestUsers()).thenThrow(fakeFeignException(500));

            var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> vempainAdminService.listIngestUsers());
            assertThat(ex.getStatusCode()
                .value()).isEqualTo(502);
        }

        @Test
        void emptyAnswerBecomesBadGateway() {
            when(vempainAdminFileIngestClient.listIngestUsers()).thenReturn(ResponseEntity.ok(null));

            var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> vempainAdminService.listIngestUsers());
            assertThat(ex.getStatusCode()
                .value()).isEqualTo(502);
        }
    }

    @Nested
    @DisplayName("listIngestUnits")
    class ListIngestUnits {

        @Test
        void returnsTheUnitsOfTheAdminBackend() {
            var unit = FileIngestUnitResponse.builder()
                .id(3L)
                .name("Editors")
                .description("Site editors")
                .build();
            when(vempainAdminFileIngestClient.listIngestUnits()).thenReturn(ResponseEntity.ok(List.of(unit)));

            assertThat(vempainAdminService.listIngestUnits()).containsExactly(unit);
        }

        @Test
        void forbiddenTriggersReauthentication() {
            when(vempainAdminFileIngestClient.listIngestUnits()).thenThrow(fakeFeignException(401));

            assertThrows(VempainAuthenticationException.class, () -> vempainAdminService.listIngestUnits());
        }

        @Test
        void otherFailuresBecomeBadGateway() {
            when(vempainAdminFileIngestClient.listIngestUnits()).thenThrow(fakeFeignException(503));

            var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> vempainAdminService.listIngestUnits());
            assertThat(ex.getStatusCode()
                .value()).isEqualTo(502);
        }

        @Test
        void emptyAnswerBecomesBadGateway() {
            when(vempainAdminFileIngestClient.listIngestUnits()).thenReturn(ResponseEntity.status(204)
                .build());

            var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> vempainAdminService.listIngestUnits());
            assertThat(ex.getStatusCode()
                .value()).isEqualTo(502);
        }
    }

    @Nested
    @DisplayName("deleteSiteFile")
    class DeleteSiteFile {

        @Test
        void removesTheSiteFileAndIgnoresOneThatIsAlreadyGone() {
            vempainAdminService.deleteSiteFile(5L);
            verify(vempainAdminFileIngestClient).deleteSiteFile(5L);

            doThrow(fakeFeignException(404)).when(vempainAdminFileIngestClient)
                .deleteSiteFile(6L);
            vempainAdminService.deleteSiteFile(6L);
            verify(vempainAdminFileIngestClient).deleteSiteFile(6L);
        }

        @Test
        void refusedTokensAndOtherFailuresAreThrown() {
            doThrow(fakeFeignException(403)).when(vempainAdminFileIngestClient)
                .deleteSiteFile(7L);
            assertThrows(VempainAuthenticationException.class, () -> vempainAdminService.deleteSiteFile(7L));

            doThrow(fakeFeignException(500)).when(vempainAdminFileIngestClient)
                .deleteSiteFile(8L);
            assertThrows(FeignException.class, () -> vempainAdminService.deleteSiteFile(8L));
        }
    }

    // ------------------------------------------------------------------
    // ACL lookups of earlier publishes
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("getSiteFileAcls / getSiteFileAclsByFileId / getGalleryAcls")
    class AclLookups {

        private final FileIngestSiteFileAclResponse siteFileAcls = FileIngestSiteFileAclResponse.builder()
            .siteFileId(42L)
            .fileId(55L)
            .acls(List.of(FileIngestAclResponse.builder()
                .userId(12L)
                .readPrivilege(true)
                .build()))
            .build();

        @Test
        void returnsTheGranteesOfASiteFile() {
            when(vempainAdminFileIngestClient.getSiteFileAcls(42L)).thenReturn(ResponseEntity.ok(siteFileAcls));

            assertThat(vempainAdminService.getSiteFileAcls(42L)).contains(siteFileAcls);
        }

        @Test
        void returnsTheGranteesOfTheSiteFileOfAFile() {
            when(vempainAdminFileIngestClient.getSiteFileAclsByFileId(55L)).thenReturn(ResponseEntity.ok(siteFileAcls));

            assertThat(vempainAdminService.getSiteFileAclsByFileId(55L)).contains(siteFileAcls);
        }

        @Test
        void returnsTheGranteesAndSiteFilesOfAGallery() {
            var gallery = FileIngestGalleryAclResponse.builder()
                .galleryId(77L)
                .shortname("Summer")
                .siteFileIds(List.of(42L, 43L))
                .acls(List.of())
                .build();
            when(vempainAdminFileIngestClient.getGalleryAcls(77L)).thenReturn(ResponseEntity.ok(gallery));

            assertThat(vempainAdminService.getGalleryAcls(77L)).contains(gallery);
        }

        @Test
        void aResourceTheAdminBackendNoLongerHoldsIsEmpty() {
            when(vempainAdminFileIngestClient.getSiteFileAcls(404L)).thenThrow(fakeFeignException(404));
            when(vempainAdminFileIngestClient.getSiteFileAclsByFileId(404L)).thenThrow(fakeFeignException(404));
            when(vempainAdminFileIngestClient.getGalleryAcls(404L)).thenThrow(fakeFeignException(404));

            assertThat(vempainAdminService.getSiteFileAcls(404L)).isEmpty();
            assertThat(vempainAdminService.getSiteFileAclsByFileId(404L)).isEmpty();
            assertThat(vempainAdminService.getGalleryAcls(404L)).isEmpty();
        }

        @Test
        void aRefusedTokenIsAnAuthenticationFailure() {
            when(vempainAdminFileIngestClient.getSiteFileAcls(42L)).thenThrow(fakeFeignException(401));
            when(vempainAdminFileIngestClient.getGalleryAcls(77L)).thenThrow(fakeFeignException(403));

            assertThrows(VempainAuthenticationException.class, () -> vempainAdminService.getSiteFileAcls(42L));
            assertThrows(VempainAuthenticationException.class, () -> vempainAdminService.getGalleryAcls(77L));
            verify(fileMetrics).adminBackendRejected(401);
            verify(fileMetrics).adminBackendRejected(403);
        }

        @Test
        void otherFailuresAndEmptyAnswersBecomeBadGateway() {
            when(vempainAdminFileIngestClient.getSiteFileAcls(42L)).thenThrow(fakeFeignException(500));
            when(vempainAdminFileIngestClient.getGalleryAcls(77L)).thenReturn(ResponseEntity.ok(null));
            when(vempainAdminFileIngestClient.getSiteFileAclsByFileId(55L)).thenReturn(ResponseEntity.status(204)
                .build());

            for (Runnable call : List.<Runnable>of(() -> vempainAdminService.getSiteFileAcls(42L), () -> vempainAdminService.getGalleryAcls(77L),
                    () -> vempainAdminService.getSiteFileAclsByFileId(55L))) {
                var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, call::run);
                assertThat(ex.getStatusCode()
                    .value()).isEqualTo(502);
            }
        }
    }

    @Test
    void getDataSetAclsAnswersTheGranteesAndTreatsAMissingDataSetAsEmpty() {
        var grantees = fi.poltsi.vempain.admin.api.response.DataSetAclResponse.builder()
            .identifier("music_library")
            .aclId(42L)
            .acls(List.of())
            .build();
        when(vempainAdminDataClient.getDataSetAcls("music_library")).thenReturn(ResponseEntity.ok(grantees));
        when(vempainAdminDataClient.getDataSetAcls("gone")).thenThrow(fakeFeignException(404));
        when(vempainAdminDataClient.getDataSetAcls("refused")).thenThrow(fakeFeignException(403));

        assertEquals(Optional.of(grantees), vempainAdminService.getDataSetAcls("music_library"));
        assertTrue(vempainAdminService.getDataSetAcls("gone")
            .isEmpty());
        assertThrows(VempainAuthenticationException.class, () -> vempainAdminService.getDataSetAcls("refused"));
    }

    @Test
    void getPagedDataSetsAnswersTheAdminPageAndMapsTheFailures() {
        var request = new fi.poltsi.vempain.admin.api.request.DataPagedRequest();
        request.setPage(0);
        request.setSize(25);
        var page = fi.poltsi.vempain.auth.api.response.PagedResponse.of(List.of(fi.poltsi.vempain.admin.api.response.DataSummaryResponse.builder()
            .identifier("music_library")
            .build()), 0, 25, 1, 1, true, true);
        when(vempainAdminDataClient.getPagedDataSets(request)).thenReturn(ResponseEntity.ok(page));

        assertEquals(page, vempainAdminService.getPagedDataSets(request));

        // re-stubbing a throwing mock needs the doThrow form, a when(...) call would trigger the earlier throw
        org.mockito.Mockito.doThrow(fakeFeignException(403))
            .when(vempainAdminDataClient)
            .getPagedDataSets(request);
        assertThrows(VempainAuthenticationException.class, () -> vempainAdminService.getPagedDataSets(request));
        org.mockito.Mockito.doThrow(fakeFeignException(500))
            .when(vempainAdminDataClient)
            .getPagedDataSets(request);
        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY,
                assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> vempainAdminService.getPagedDataSets(request))
                    .getStatusCode());
    }
}
