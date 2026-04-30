package fi.poltsi.vempain.file.service;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.admin.api.response.file.FileIngestResponse;
import fi.poltsi.vempain.admin.api.response.file.SiteFileResponse;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.feign.VempainAdminFileClient;
import fi.poltsi.vempain.file.feign.VempainAdminFileIngestClient;
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
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private ObjectMapper objectMapper;

    @InjectMocks
    private VempainAdminService vempainAdminService;

    private FeignException fakeFeignException(int status) {
        return FeignException.errorStatus("test",
                feign.Response.builder()
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
        void success_returnsResponse(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("export.jpg");
            Files.writeString(file, "img-content");

            var request = FileIngestRequest.builder()
                                           .mimeType("image/jpeg")
                                           .build();
            var expected = new FileIngestResponse(99L, 42L, false);

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(vempainAdminFileIngestClient.ingest(anyString(), any()))
                    .thenReturn(ResponseEntity.ok(expected));

            var result = vempainAdminService.uploadAsSiteFile(file.toFile(), request);
            assertThat(result.getSiteFileId()).isEqualTo(42L);
        }

        @Test
        void nullResponse_throwsAuthException(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("export.jpg");
            Files.writeString(file, "img");
            var request = FileIngestRequest.builder().mimeType("image/jpeg").build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(vempainAdminFileIngestClient.ingest(anyString(), any())).thenReturn(null);

            assertThrows(VempainAuthenticationException.class,
                    () -> vempainAdminService.uploadAsSiteFile(file.toFile(), request));
        }

        @Test
        void nonSuccessStatus_throwsAuthException(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("export.jpg");
            Files.writeString(file, "img");
            var request = FileIngestRequest.builder().mimeType("image/jpeg").build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(vempainAdminFileIngestClient.ingest(anyString(), any()))
                    .thenReturn(ResponseEntity.badRequest().build());

            assertThrows(VempainAuthenticationException.class,
                    () -> vempainAdminService.uploadAsSiteFile(file.toFile(), request));
        }

        @Test
        void forbidden403_throwsAuthException(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("export.jpg");
            Files.writeString(file, "img");
            var request = FileIngestRequest.builder().mimeType("image/jpeg").build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(vempainAdminFileIngestClient.ingest(anyString(), any()))
                    .thenThrow(fakeFeignException(403));

            assertThrows(VempainAuthenticationException.class,
                    () -> vempainAdminService.uploadAsSiteFile(file.toFile(), request));
        }

        @Test
        void feignException_nonForbidden_rethrows(@TempDir Path tempDir) throws IOException {
            var file = tempDir.resolve("export.jpg");
            Files.writeString(file, "img");
            var request = FileIngestRequest.builder().mimeType("image/jpeg").build();

            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            var feignEx = fakeFeignException(500);
            when(vempainAdminFileIngestClient.ingest(anyString(), any())).thenThrow(feignEx);

            assertThrows(FeignException.class,
                    () -> vempainAdminService.uploadAsSiteFile(file.toFile(), request));
        }
    }

    // ------------------------------------------------------------------
    // getPageableSiteFiles
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("getPageableSiteFiles")
    class GetPageableSiteFiles {

        @Test
        void success_returnsBody() {
            var mockBody = new PagedResponse<SiteFileResponse>();
            when(vempainAdminFileClient.getPageableSiteFiles(
                    any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(ResponseEntity.ok(mockBody));

            var result = vempainAdminService.getPageableSiteFiles(
                    FileTypeEnum.IMAGE, 0, 10, "id", Sort.Direction.ASC, null, null);
            assertThat(result).isNotNull();
        }

        @Test
        void nullResponse_returnsNull() {
            when(vempainAdminFileClient.getPageableSiteFiles(
                    any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(null);

            var result = vempainAdminService.getPageableSiteFiles(
                    FileTypeEnum.IMAGE, 0, 10, "id", Sort.Direction.ASC, null, null);
            assertNull(result);
        }

        @Test
        void nonSuccessStatus_returnsNull() {
            when(vempainAdminFileClient.getPageableSiteFiles(
                    any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(ResponseEntity.badRequest().build());

            var result = vempainAdminService.getPageableSiteFiles(
                    FileTypeEnum.IMAGE, 0, 10, "id", Sort.Direction.ASC, null, null);
            assertNull(result);
        }

        @Test
        void feignException_returnsNull() {
            when(vempainAdminFileClient.getPageableSiteFiles(
                    any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(fakeFeignException(503));

            var result = vempainAdminService.getPageableSiteFiles(
                    FileTypeEnum.IMAGE, 0, 10, "id", Sort.Direction.ASC, null, null);
            assertNull(result);
        }
    }
}
