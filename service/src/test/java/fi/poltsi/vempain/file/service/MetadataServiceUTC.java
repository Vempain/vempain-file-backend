package fi.poltsi.vempain.file.service;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.common.api.TaskStatusEnum;
import fi.poltsi.vempain.common.task.TaskProgress;
import fi.poltsi.vempain.common.task.TaskProgressStore;
import fi.poltsi.vempain.common.task.TaskRunner;
import fi.poltsi.vempain.file.api.request.MetadataFilesRequest;
import fi.poltsi.vempain.file.api.request.MetadataSearchRequest;
import fi.poltsi.vempain.file.api.request.MetadataValueUpdateRequest;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.MetadataEntity;
import fi.poltsi.vempain.file.entity.ThumbFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.repository.MetadataRepositoryCustom.MetadataSummaryRow;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.repository.files.ThumbFileRepository;
import fi.poltsi.vempain.file.tools.TestExifTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (UTC) for {@link MetadataService}: paging/sorting of the two list endpoints, the synchronous validation and ACL checks of the value rewrite, and
 * the task body against a real file (ExifTool is required on the host).
 */
@ExtendWith(MockitoExtension.class)
class MetadataServiceUTC {

    @Mock
    private MetadataRepository metadataRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private FileResponseEnricher fileResponseEnricher;
    @Mock
    private ExportFileRepository exportFileRepository;
    @Mock
    private ThumbFileRepository thumbFileRepository;
    @Mock
    private DirectoryProcessorService directoryProcessorService;
    @Mock
    private ThumbnailGenerationService thumbnailGenerationService;
    @Mock
    private FileAclService fileAclService;
    @Mock
    private ApplicationContext applicationContext;

    private final List<Runnable> queued = new ArrayList<>();

    private MetadataService metadataService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        var taskRunner = new TaskRunner(new TaskProgressStore(), queued::add);
        metadataService = new MetadataService(metadataRepository, fileRepository, fileResponseEnricher, exportFileRepository, thumbFileRepository,
                directoryProcessorService, thumbnailGenerationService, fileAclService, taskRunner, applicationContext, TestExifTool.service());
        ReflectionTestUtils.setField(metadataService, "originalRootDirectory", tempDir.toString());
        ReflectionTestUtils.setField(metadataService, "exportRootDirectory", tempDir.resolve("export")
            .toString());
        ReflectionTestUtils.setField(metadataService, "thumbnailMinimumSize", 100);
        ReflectionTestUtils.setField(metadataService, "thumbnailQuality", 0.5f);
        lenient().when(applicationContext.getBean(MetadataService.class))
            .thenReturn(metadataService);
        var principal = new UserDetailsImpl(1L, "admin", "Admin", "admin@nohost.nodomain", "password", Set.of(), List.of());
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("findSummaries")
    class FindSummaries {

        @Test
        void mapsRowsFlagsReadOnlyGroupsAndClampsThePage() {
            var rows = List.of(new MetadataSummaryRow("XMP-dc", "Creator", "Jane", 3L), new MetadataSummaryRow("Composite", "ImageSize", "8x8", 1L));
            var pageable = ArgumentCaptor.forClass(Pageable.class);
            when(metadataRepository.searchSummaries(any(), any(), any(), any(Boolean.class), pageable.capture()))
                .thenReturn(new PageImpl<>(rows, Pageable.ofSize(200), 2));

            var response = metadataService.findSummaries(MetadataSearchRequest.builder()
                .page(-1)
                .size(5000)
                .sortBy("file_count")
                .direction(Sort.Direction.DESC)
                .metadataKey("creat")
                .build());

            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getContent()
                .getFirst()
                .isWritable()).isTrue();
            assertThat(response.getContent()
                .getFirst()
                .getFileCount()).isEqualTo(3L);
            assertThat(response.getContent()
                .get(1)
                .isWritable()).as("Composite values can not be written back")
                .isFalse();
            assertThat(pageable.getValue()
                .getPageNumber()).isZero();
            assertThat(pageable.getValue()
                .getPageSize()).isEqualTo(MetadataService.MAX_PAGE_SIZE);
            assertThat(pageable.getValue()
                .getSort()
                .getOrderFor("file_count")
                .getDirection()).isEqualTo(Sort.Direction.DESC);
            verify(metadataRepository).searchSummaries(null, "creat", null, false, pageable.getValue());
        }

        @Test
        void unknownSortFallsBackToTheKey() {
            var pageable = ArgumentCaptor.forClass(Pageable.class);
            when(metadataRepository.searchSummaries(any(), any(), any(), any(Boolean.class), pageable.capture())).thenReturn(new PageImpl<>(List.of()));

            metadataService.findSummaries(MetadataSearchRequest.builder()
                .page(0)
                .size(10)
                .sortBy("drop table")
                .caseSensitive(true)
                .build());

            assertThat(pageable.getValue()
                .getSort()
                .getOrderFor("metadata_key")).isNotNull();
            verify(metadataRepository).searchSummaries(null, null, null, true, pageable.getValue());
        }
    }

    @Nested
    @DisplayName("findFiles")
    class FindFiles {

        @Test
        @SuppressWarnings("unchecked")
        void listsReadableFilesWithTheRequestedSort() {
            Specification<FileEntity> readable = (root, query, cb) -> cb.conjunction();
            when(fileAclService.<FileEntity>readableFiles(any())).thenReturn(readable);
            var file = new ImageFileEntity();
            file.setId(9L);
            file.setFilename("a.jpg");
            var pageable = ArgumentCaptor.forClass(Pageable.class);
            when(fileRepository.findAll(any(Specification.class), pageable.capture())).thenReturn(new PageImpl<>(List.of(file)));
            var response = new FileResponse();
            response.setId(9L);
            when(fileResponseEnricher.<FileResponse>toResponses(List.of(file))).thenReturn(List.of(response));

            var page = metadataService.findFiles(MetadataFilesRequest.builder()
                .metadataGroup("XMP-dc")
                .metadataKey("Creator")
                .metadataValue("Jane")
                .page(0)
                .size(10)
                .sortBy("filesize")
                .direction(Sort.Direction.DESC)
                .fileType(FileTypeEnum.IMAGE)
                .filename("a")
                .build());

            assertThat(page.getContent()).extracting(FileResponse::getId)
                .containsExactly(9L);
            assertThat(pageable.getValue()
                .getSort()
                .getOrderFor("filesize")
                .getDirection()).isEqualTo(Sort.Direction.DESC);
            verify(fileAclService).readableFiles(any());
        }
    }

    @Nested
    @DisplayName("updateValueAsTask")
    class UpdateValueAsTask {

        @Test
        void readOnlyGroupIsRejectedBeforeAnyLookup() {
            var request = request("Composite", "ImageSize", "8x8", "9x9");

            assertThrows(IllegalArgumentException.class, () -> metadataService.updateValueAsTask(request));

            verify(metadataRepository, never()).findFileIdsByGroupKeyAndValue(anyString(), anyString(), anyString());
            assertThat(queued).isEmpty();
        }

        @Test
        void unsafeKeyIsRejectedBeforeAnyLookup() {
            var request = request("XMP-dc", "Creator<", "Jane", "Joe");

            assertThrows(IllegalArgumentException.class, () -> metadataService.updateValueAsTask(request));

            verify(metadataRepository, never()).findFileIdsByGroupKeyAndValue(anyString(), anyString(), anyString());
        }

        @Test
        void valueWithoutFilesIsRejected() {
            when(metadataRepository.findFileIdsByGroupKeyAndValue("XMP-dc", "Creator", "Nobody")).thenReturn(List.of());

            assertThrows(IllegalArgumentException.class, () -> metadataService.updateValueAsTask(request("XMP-dc", "Creator", "Nobody", "x")));

            verify(fileAclService, never()).requireModifyBulk(any());
            assertThat(queued).isEmpty();
        }

        @Test
        void deniedFileFailsBeforeTheTaskIsSubmitted() {
            var file = new ImageFileEntity();
            file.setId(1L);
            when(metadataRepository.findFileIdsByGroupKeyAndValue("XMP-dc", "Creator", "Jane")).thenReturn(List.of(1L));
            when(fileRepository.findAllById(any())).thenReturn(List.of(file));
            org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(fileAclService)
                .requireModifyBulk(List.of(file));

            assertThrows(AccessDeniedException.class, () -> metadataService.updateValueAsTask(request("XMP-dc", "Creator", "Jane", "Joe")));

            assertThat(queued).isEmpty();
        }

        @Test
        void submitsOneStepPerFile() {
            var first = new ImageFileEntity();
            first.setId(1L);
            var second = new ImageFileEntity();
            second.setId(2L);
            when(metadataRepository.findFileIdsByGroupKeyAndValue("XMP-dc", "Creator", "Jane")).thenReturn(List.of(1L, 2L));
            when(fileRepository.findAllById(any())).thenReturn(List.of(first, second));

            var progress = metadataService.updateValueAsTask(request("XMP-dc", "Creator", "Jane", ""));

            assertThat(progress.getType()).isEqualTo("METADATA_UPDATE_VALUE");
            assertThat(progress.getTitle()).startsWith("Remove XMP-dc:Creator in 2 files");
            assertThat(progress.toResponse()
                .getTotalSteps()).isEqualTo(2);
            assertThat(progress.getStatus()).isEqualTo(TaskStatusEnum.QUEUED);
            assertThat(queued).as("the work is queued, not run in the request")
                .hasSize(1);
        }
    }

    @Nested
    @DisplayName("applyValueUpdate")
    class ApplyValueUpdate {

        @Test
        void missingOriginalFileFailsTheStep() {
            var file = new ImageFileEntity();
            file.setId(1L);
            file.setFilename("missing.jpg");
            file.setFilePath("/");
            when(fileRepository.findAllById(any())).thenReturn(List.of(file));
            when(metadataRepository.findFirstByFileIdAndMetadataGroupAndMetadataKey(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
            var progress = TaskProgress.unmanaged("t", "METADATA_UPDATE_VALUE", "title", 1L, 1, "{}");

            assertThrows(IllegalStateException.class,
                    () -> metadataService.applyValueUpdate(request("XMP-dc", "Creator", "Jane", "Joe"), List.of(1L), progress));
        }

        @Test
        void rewritesTheFileRefreshesTheRowAndRegistersTheUndo() throws Exception {
            var image = tempDir.resolve("creator.jpg")
                .toFile();
            ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", image);
            TestExifTool.service()
                .writeValue(image, "XMP-dc", "Creator", "Jane");
            var file = new ImageFileEntity();
            file.setId(1L);
            file.setFilename(image.getName());
            file.setFilePath("/");
            when(fileRepository.findAllById(any())).thenReturn(List.of(file));
            when(metadataRepository.findFirstByFileIdAndMetadataGroupAndMetadataKey(1L, "XMP-dc", "Creator")).thenReturn(Optional.of(MetadataEntity.builder()
                .metadataValue("Jane")
                .build()));
            when(exportFileRepository.findByFileId(1L)).thenReturn(Optional.empty());
            when(directoryProcessorService.refreshExistingOriginalFile(any(), any())).thenReturn(true);
            var progress = TaskProgress.unmanaged("t", "METADATA_UPDATE_VALUE", "title", 1L, 1, "{}");

            metadataService.applyValueUpdate(request("XMP-dc", "Creator", "Jane", "Joe"), List.of(1L), progress);

            assertThat(TestExifTool.service()
                .readMetadata(image)
                .getJSONObject("XMP-dc")
                .getString("Creator")).isEqualTo("Joe");
            verify(directoryProcessorService).refreshExistingOriginalFile(file, image);
            verify(fileRepository).save(file);
            assertThat(file.getModifier()).isEqualTo(1L);
            assertThat(progress.toResponse()
                .getCompletedSteps()).isEqualTo(1);
        }

        @Test
        void revertValueWritesThePreviousValueBack() throws Exception {
            var image = tempDir.resolve("revert.jpg")
                .toFile();
            ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", image);
            TestExifTool.service()
                .writeValue(image, "XMP-dc", "Creator", "Joe");
            var file = new ImageFileEntity();
            file.setId(1L);
            file.setFilename(image.getName());
            file.setFilePath("/");
            when(fileRepository.findById(1L)).thenReturn(Optional.of(file));
            when(exportFileRepository.findByFileId(1L)).thenReturn(Optional.empty());
            when(directoryProcessorService.refreshExistingOriginalFile(any(), any())).thenReturn(true);

            metadataService.revertValue(1L, "XMP-dc", "Creator", "Jane");

            assertThat(TestExifTool.service()
                .readMetadata(image)
                .getJSONObject("XMP-dc")
                .getString("Creator")).isEqualTo("Jane");
            verify(fileRepository).save(file);
        }

        @Test
        void revertValueIgnoresUnknownFiles() {
            when(fileRepository.findById(404L)).thenReturn(Optional.empty());

            metadataService.revertValue(404L, "XMP-dc", "Creator", "Jane");

            verify(exportFileRepository, never()).findByFileId(anyLong());
        }
    }

    private static MetadataValueUpdateRequest request(String group, String key, String value, String newValue) {
        return MetadataValueUpdateRequest.builder()
            .metadataGroup(group)
            .metadataKey(key)
            .metadataValue(value)
            .newValue(newValue)
            .build();
    }

    @Nested
    @DisplayName("rewrite side effects")
    class RewriteSideEffects {

        private ImageFileEntity imageWithCreator(String name) throws Exception {
            var image = tempDir.resolve(name)
                .toFile();
            ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", image);
            TestExifTool.service()
                .writeValue(image, "XMP-dc", "Creator", "Jane");
            var file = new ImageFileEntity();
            file.setId(1L);
            file.setFilename(name);
            file.setFilePath("/");
            when(fileRepository.findAllById(any())).thenReturn(List.of(file));
            when(metadataRepository.findFirstByFileIdAndMetadataGroupAndMetadataKey(1L, "XMP-dc", "Creator")).thenReturn(Optional.of(MetadataEntity.builder()
                .metadataValue("Jane")
                .build()));
            lenient().when(directoryProcessorService.refreshExistingOriginalFile(any(), any()))
                .thenReturn(true);
            return file;
        }

        @Test
        void theQueuedTaskRewritesTheFileThroughTheProxy() throws Exception {
            imageWithCreator("queued.jpg");
            when(metadataRepository.findFileIdsByGroupKeyAndValue("XMP-dc", "Creator", "Jane")).thenReturn(List.of(1L));
            when(exportFileRepository.findByFileId(1L)).thenReturn(Optional.empty());

            var progress = metadataService.updateValueAsTask(request("XMP-dc", "Creator", "Jane", "Joe"));
            queued.getFirst()
                .run();

            assertThat(progress.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
            assertThat(TestExifTool.service()
                .readMetadata(tempDir.resolve("queued.jpg")
                    .toFile())
                .getJSONObject("XMP-dc")
                .getString("Creator")).isEqualTo("Joe");
        }

        @Test
        void theExportFileAndItsThumbnailFollowTheRewrite() throws Exception {
            var file = imageWithCreator("exported.jpg");
            var exportDir = Files.createDirectories(tempDir.resolve("export"));
            var exported = exportDir.resolve("exported.jpg")
                .toFile();
            ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", exported);
            var export = ExportFileEntity.builder()
                .id(3L)
                .file(file)
                .filePath("/")
                .filename("exported.jpg")
                .sha256sum("stale")
                .build();
            when(exportFileRepository.findByFileId(1L)).thenReturn(Optional.of(export));
            var thumbnail = new ThumbFileEntity();
            thumbnail.setFilePath("/thumb");
            thumbnail.setFilename("exported.jpeg");
            var thumbnailFile = Files.createDirectories(tempDir.resolve("thumb"))
                .resolve("exported.jpeg");
            Files.writeString(thumbnailFile, "thumb");
            when(thumbFileRepository.findThumbnailIdByTargetFileId(1L)).thenReturn(Optional.of(9L));
            when(thumbFileRepository.findById(9L)).thenReturn(Optional.of(thumbnail));
            var progress = TaskProgress.unmanaged("t", "METADATA_UPDATE_VALUE", "title", 1L, 1, "{}");

            metadataService.applyValueUpdate(request("XMP-dc", "Creator", "Jane", "Joe"), List.of(1L), progress);

            assertThat(TestExifTool.service()
                .readMetadata(exported)
                .getJSONObject("XMP-dc")
                .getString("Creator")).isEqualTo("Joe");
            assertThat(export.getSha256sum()).isNotEqualTo("stale");
            assertThat(export.getFilesize()).isEqualTo(exported.length());
            assertThat(thumbnailFile).doesNotExist();
            verify(thumbFileRepository).delete(thumbnail);
            verify(exportFileRepository).save(export);
            verify(thumbnailGenerationService).generateThumbnail(export, tempDir.toString(), exportDir.toString(), 100, 0.5f);
        }

        @Test
        void aFailingThumbnailFailsTheRewrite() throws Exception {
            var file = imageWithCreator("failing.jpg");
            var exportDir = Files.createDirectories(tempDir.resolve("export"));
            ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", exportDir.resolve("failing.jpg")
                .toFile());
            when(exportFileRepository.findByFileId(1L)).thenReturn(Optional.of(ExportFileEntity.builder()
                .id(3L)
                .file(file)
                .filePath("/")
                .filename("failing.jpg")
                .build()));
            when(thumbFileRepository.findThumbnailIdByTargetFileId(1L)).thenReturn(Optional.empty());
            doThrow(new IllegalStateException("no thumbnail")).when(thumbnailGenerationService)
                .generateThumbnail(any(), any(), any(), any(Integer.class), any(Float.class));
            var progress = TaskProgress.unmanaged("t", "METADATA_UPDATE_VALUE", "title", 1L, 1, "{}");

            var failure = assertThrows(IllegalStateException.class,
                    () -> metadataService.applyValueUpdate(request("XMP-dc", "Creator", "Jane", "Joe"), List.of(1L), progress));
            assertThat(failure).hasMessage("Failed to update metadata XMP-dc:Creator of file 1");
            assertThat(failure.getCause()).hasMessage("Failed to regenerate thumbnail");
        }
    }

    @Test
    void summarySortAcceptsTheGroupAndValueColumns() {
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        when(metadataRepository.searchSummaries(any(), any(), any(), any(Boolean.class), pageable.capture())).thenReturn(new PageImpl<>(List.of()));

        metadataService.findSummaries(MetadataSearchRequest.builder()
            .page(0)
            .size(10)
            .sortBy("group")
            .build());
        metadataService.findSummaries(MetadataSearchRequest.builder()
            .page(0)
            .size(10)
            .sortBy("metadata_value")
            .build());

        assertThat(pageable.getAllValues()
            .get(0)
            .getSort()
            .getOrderFor("metadata_group")).isNotNull();
        assertThat(pageable.getAllValues()
            .get(1)
            .getSort()
            .getOrderFor("metadata_value")).isNotNull();
    }
}
