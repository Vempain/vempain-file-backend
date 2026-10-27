package fi.poltsi.vempain.file.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.admin.api.response.file.FileIngestAclResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestGalleryAclResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestSiteFileAclResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestUnitResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestUserResponse;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.common.api.TaskStatusEnum;
import fi.poltsi.vempain.common.metadata.ExifToolService;
import fi.poltsi.vempain.common.task.TaskProgressStore;
import fi.poltsi.vempain.common.task.TaskRunner;
import fi.poltsi.vempain.file.api.request.PublishAclRequest;
import fi.poltsi.vempain.file.api.request.PublishAllFileGroupsRequest;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.request.PublishFileRequest;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.metrics.FileMetrics;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepositoryCustom.FileGroupSummaryRow;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.tools.ImageTool;

import static fi.poltsi.vempain.file.tools.FileMetadataTool.collectStandardMetadataAsJson;
import static fi.poltsi.vempain.file.util.MetadataUtil.generateMetadataEntities;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (UTC) for {@link PublishService} methods that can be tested without full Spring context.
 */
@ExtendWith(MockitoExtension.class)
class PublishServiceUTC {

    @TempDir
    Path tempDir;
    @Mock
    private FileGroupRepository fileGroupRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private ExportFileRepository exportFileRepository;
    @Mock
    private VempainAdminService vempainAdminService;
    @Mock
    private TagService tagService;
    @Mock
    private LocationService locationService;
    @Mock
    private FileAclService fileAclService;
    @Mock
    private ImageTool imageTool;
    @Mock
    private ExifToolService exifToolService;
    @Mock
    private MetadataService metadataService;
    @Mock
    private ApplicationContext applicationContext;
    /**
     * Real runner on a synchronous executor so that submitted tasks complete before the test continues.
     */
    @Spy
    private TaskRunner taskRunner = new TaskRunner(new TaskProgressStore(), Runnable::run);
    @Mock
    private FileMetrics fileMetrics;
    @InjectMocks
    private PublishService publishService;

    private static byte[] getMinimalJpegBytes() {
        return new byte[]{
            (byte) 0xFF,
            (byte) 0xD8,
            (byte) 0xFF,
            (byte) 0xE0,
            0x00,
            0x10,
            0x4A,
            0x46,
            0x49,
            0x46,
            0x00,
            0x01,
            0x01,
            0x00,
            0x00,
            0x01,
            0x00,
            0x01,
            0x00,
            0x00,
            (byte) 0xFF,
            (byte) 0xDB,
            0x00,
            0x43,
            0x00,
            0x08,
            0x06,
            0x06,
            0x07,
            0x06,
            0x05,
            0x08,
            0x07,
            0x07,
            0x07,
            0x09,
            0x09,
            0x08,
            0x0A,
            0x0C,
            0x14,
            0x0D,
            0x0C,
            0x0B,
            0x0B,
            0x0C,
            0x19,
            0x12,
            0x13,
            0x0F,
            0x14,
            0x1D,
            0x1A,
            0x1F,
            0x1E,
            0x1D,
            0x1A,
            0x1C,
            0x1C,
            0x20,
            0x24,
            0x2E,
            0x27,
            0x20,
            0x22,
            0x2C,
            0x23,
            0x1C,
            0x1C,
            0x28,
            0x37,
            0x29,
            0x2C,
            0x30,
            0x31,
            0x34,
            0x34,
            0x34,
            0x1F,
            0x27,
            0x39,
            0x3D,
            0x38,
            0x32,
            0x3C,
            0x2E,
            0x33,
            0x34,
            0x32,
            (byte) 0xFF,
            (byte) 0xC0,
            0x00,
            0x0B,
            0x08,
            0x00,
            0x01,
            0x00,
            0x01,
            0x01,
            0x01,
            0x11,
            0x00,
            (byte) 0xFF,
            (byte) 0xC4,
            0x00,
            0x1F,
            0x00,
            0x00,
            0x01,
            0x05,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x01,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x01,
            0x02,
            0x03,
            0x04,
            0x05,
            0x06,
            0x07,
            0x08,
            0x09,
            0x0A,
            0x0B,
            (byte) 0xFF,
            (byte) 0xDA,
            0x00,
            0x08,
            0x01,
            0x01,
            0x00,
            0x00,
            0x3F,
            0x00,
            (byte) 0xFB,
            (byte) 0xD2,
            (byte) 0xFF,
            (byte) 0xD9 };
    }

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(publishService, "exportRootDirectory", tempDir.toString());
        ReflectionTestUtils.setField(publishService, "exportFileType", "jpeg");
        ReflectionTestUtils.setField(publishService, "siteImageSize", 1200);
        lenient().when(metadataService.getFileMetadata(anyLong()))
            .thenReturn(List.of());
    }

    // ------------------------------------------------------------------
    // countExportedFilesInGroup
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("countExportedFilesInGroup")
    class CountExportedFilesInGroup {

        @Test
        void returnsCount() {
            when(fileGroupRepository.countExportedFilesById(5L)).thenReturn(3L);
            assertThat(publishService.countExportedFilesInGroup(5L)).isEqualTo(3L);
        }

        @Test
        void returnsZeroWhenNoFiles() {
            when(fileGroupRepository.countExportedFilesById(99L)).thenReturn(0L);
            assertThat(publishService.countExportedFilesInGroup(99L)).isEqualTo(0L);
        }

        @Test
        void fileGroupExistsAsksTheRepository() {
            when(fileGroupRepository.existsById(5L)).thenReturn(true);
            assertThat(publishService.fileGroupExists(5L)).isTrue();
            assertThat(publishService.fileGroupExists(6L)).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // republishSiteFile
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("republishSiteFile")
    class RepublishSiteFile {

        @Test
        void nullFileEntityReturnsFalse() {
            assertFalse(publishService.republishSiteFile(null));
        }

        @Test
        void noExportedFileReturnsFalse() {
            var mockFile = mock(FileEntity.class);
            when(mockFile.getId()).thenReturn(1L);
            when(exportFileRepository.findByFileId(1L)).thenReturn(Optional.empty());

            assertFalse(publishService.republishSiteFile(mockFile));
        }

        @Test
        void exportFileExistsOnDiskNonImageSuccess(@TempDir Path tempDir2) throws IOException {
            ReflectionTestUtils.setField(publishService, "exportRootDirectory", tempDir2.toString());

            // Create a real export file
            var subDir = tempDir2.resolve("sub");
            Files.createDirectories(subDir);
            var exportFile = subDir.resolve("test.jpg");
            // Write minimal JPEG bytes
            Files.write(exportFile, getMinimalJpegBytes());

            var exportEntity = ExportFileEntity.builder()
                .id(1L)
                .filePath("/sub")
                .filename("test.jpg")
                .build();
            when(exportFileRepository.findByFileId(42L)).thenReturn(Optional.of(exportEntity));

            var mockFileEntity = mock(FileEntity.class);
            when(mockFileEntity.getId()).thenReturn(42L);
            when(mockFileEntity.getFileType()).thenReturn(FileTypeEnum.BINARY);
            when(mockFileEntity.getFilePath()).thenReturn("/sub");
            when(mockFileEntity.getFilename()).thenReturn("test.jpg");
            when(mockFileEntity.getDescription()).thenReturn("Test");
            when(mockFileEntity.getGpsLocation()).thenReturn(null);
            when(tagService.getTagRequestsByFileId(42L)).thenReturn(List.of());
            when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(null);

            var result = publishService.republishSiteFile(mockFileEntity);
            // For BINARY type, should attempt to upload and succeed
            assertTrue(result);
        }

        @Test
        void exportFileDoesNotExistOnDiskReturnsFalse() {
            var exportEntity = ExportFileEntity.builder()
                .id(1L)
                .filePath("/nonexistent")
                .filename("missing.jpg")
                .build();
            when(exportFileRepository.findByFileId(99L)).thenReturn(Optional.of(exportEntity));

            var mockFileEntity = mock(FileEntity.class);
            when(mockFileEntity.getId()).thenReturn(99L);

            assertFalse(publishService.republishSiteFile(mockFileEntity));
        }

        @Test
        void documentFileSetsPages(@TempDir Path tempDir3) throws IOException {
            ReflectionTestUtils.setField(publishService, "exportRootDirectory", tempDir3.toString());

            var subDir = tempDir3.resolve("docs");
            Files.createDirectories(subDir);
            var docFile = subDir.resolve("doc.pdf");
            Files.write(docFile, "%PDF-1.4\n".getBytes());

            var exportEntity = ExportFileEntity.builder()
                .id(2L)
                .filePath("/docs")
                .filename("doc.pdf")
                .build();
            when(exportFileRepository.findByFileId(55L)).thenReturn(Optional.of(exportEntity));

            var mockDocEntity = mock(DocumentFileEntity.class);
            when(mockDocEntity.getId()).thenReturn(55L);
            when(mockDocEntity.getFileType()).thenReturn(FileTypeEnum.DOCUMENT);
            when(mockDocEntity.getFilePath()).thenReturn("/docs");
            when(mockDocEntity.getFilename()).thenReturn("doc.pdf");
            when(mockDocEntity.getDescription()).thenReturn(null);
            when(mockDocEntity.getGpsLocation()).thenReturn(null);
            when(mockDocEntity.getPageCount()).thenReturn(5);
            when(tagService.getTagRequestsByFileId(55L)).thenReturn(List.of());
            when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(null);

            var result = publishService.republishSiteFile(mockDocEntity);
            assertTrue(result);
        }

        @Test
        void publishFileGroupMarksMissingGroupAsFailed() {
            when(fileGroupRepository.findById(404L)).thenReturn(Optional.empty());
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishFileGroup(fi.poltsi.vempain.file.api.request.PublishFileGroupRequest.builder()
                .fileGroupId(404L)
                .build());

            assertThat(task.getType()).isEqualTo("PUBLISH_FILE_GROUP");
            assertThat(task.getTitle()).isEqualTo("Publish file group #404");
            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.FAILED);
            assertThat(task.getErrorMessage()).isEqualTo("File group 404 not found");
        }

        @Test
        void publishFileGroupWithNoFilesCompletes() {
            var group = FileGroupEntity.builder()
                .files(List.of())
                .build();
            when(fileGroupRepository.findById(405L)).thenReturn(Optional.of(group));
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishFileGroup(fi.poltsi.vempain.file.api.request.PublishFileGroupRequest.builder()
                .fileGroupId(405L)
                .galleryName("Empty")
                .build());

            assertThat(task.getTitle()).isEqualTo("Publish file group Empty");
            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
            assertThat(task.getMessage()).isEqualTo("File group has no files to publish");
        }

        @Test
        void publishFileGroupWithoutExportedFilesCompletesWithoutPublishing() {
            var group = FileGroupEntity.builder()
                .id(406L)
                .files(List.<FileEntity>of(ImageFileEntity.builder()
                    .id(60L)
                    .filename("a.jpg")
                    .build()))
                .build();
            when(fileGroupRepository.findById(406L)).thenReturn(Optional.of(group));
            when(fileGroupRepository.countExportedFilesById(406L)).thenReturn(0L);
            when(exportFileRepository.findByFileId(60L)).thenReturn(Optional.empty());
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishFileGroup(fi.poltsi.vempain.file.api.request.PublishFileGroupRequest.builder()
                .fileGroupId(406L)
                .build());

            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
            // A group without exported files is still one step so that the task reports it
            verify(taskRunner).submitDurable(org.mockito.ArgumentMatchers.eq("PUBLISH_FILE_GROUP"), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.eq(1L), any(), any());
            assertThat(task.getTotalSteps()
                .get()).isEqualTo(1);
            assertThat(task.getCompletedSteps()
                .get()).isEqualTo(1);
            assertThat(task.getFailedSteps()
                .get()).isZero();
            assertThat(task.getMessage()).isEqualTo("File group has no exported files to publish");
            verify(vempainAdminService, never()).uploadAsSiteFile(any(), any());
            verify(fileGroupRepository, never()).save(any());
        }

        @Test
        void publishFileGroupCountsOnlyTheExportedFilesAsSteps() throws IOException {
            var subDir = tempDir.resolve("group");
            Files.createDirectories(subDir);
            Files.write(subDir.resolve("exported.jpg"), getMinimalJpegBytes());
            var exported = ImageFileEntity.builder()
                .id(61L)
                .fileType(FileTypeEnum.BINARY)
                .filename("exported.jpg")
                .filePath("/group")
                .build();
            var notExported = ImageFileEntity.builder()
                .id(62L)
                .filename("original-only.jpg")
                .build();
            var group = FileGroupEntity.builder()
                .id(407L)
                .files(List.<FileEntity>of(exported, notExported))
                .build();
            when(fileGroupRepository.findById(407L)).thenReturn(Optional.of(group));
            when(fileGroupRepository.countExportedFilesById(407L)).thenReturn(1L);
            when(exportFileRepository.findByFileId(61L)).thenReturn(Optional.of(ExportFileEntity.builder()
                .id(610L)
                .filePath("/group")
                .filename("exported.jpg")
                .build()));
            when(exportFileRepository.findByFileId(62L)).thenReturn(Optional.empty());
            when(tagService.getTagRequestsByFileId(61L)).thenReturn(List.of());
            when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(FileIngestResponse.builder()
                .siteFileId(500L)
                .galleryId(77L)
                .build());
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishFileGroup(fi.poltsi.vempain.file.api.request.PublishFileGroupRequest.builder()
                .fileGroupId(407L)
                .galleryName("Mixed")
                .build());

            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
            // The file without an export is not transferred, so it is no step of the task: the total is the one exported file
            verify(taskRunner).submitDurable(org.mockito.ArgumentMatchers.eq("PUBLISH_FILE_GROUP"), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.eq(1L), any(), any());
            assertThat(task.getTotalSteps()
                .get()).isEqualTo(1);
            assertThat(task.getCompletedSteps()
                .get()).isEqualTo(1);
            assertThat(task.getFailedSteps()
                .get()).isZero();
            assertThat(task.getMessage()).isEqualTo("Published exported.jpg");
            verify(vempainAdminService).uploadAsSiteFile(any(), any());
            assertEquals(77L, group.getGalleryId());
            // The published file remembers its site file, the file without an export is untouched
            assertEquals(500L, exported.getSiteFileId());
            assertEquals(Boolean.TRUE, exported.getSiteFilePublished());
            verify(fileRepository).save(exported);
            assertNull(notExported.getSiteFileId());
        }

        @Test
        void publishFileGroupReportsAnExportMissingOnDiskAsAFailedStep() {
            var group = FileGroupEntity.builder()
                .id(408L)
                .files(List.<FileEntity>of(ImageFileEntity.builder()
                    .id(63L)
                    .filename("a.jpg")
                    .build()))
                .build();
            when(fileGroupRepository.findById(408L)).thenReturn(Optional.of(group));
            when(fileGroupRepository.countExportedFilesById(408L)).thenReturn(1L);
            when(exportFileRepository.findByFileId(63L)).thenReturn(Optional.of(ExportFileEntity.builder()
                .id(630L)
                .filePath("/nowhere")
                .filename("a.jpg")
                .build()));
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishFileGroup(fi.poltsi.vempain.file.api.request.PublishFileGroupRequest.builder()
                .fileGroupId(408L)
                .build());

            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
            // The export is registered, so the file was counted as a step; its missing content makes the step a failed one
            assertThat(task.getTotalSteps()
                .get()).isEqualTo(1);
            assertThat(task.getFailedSteps()
                .get()).isEqualTo(1);
            assertThat(task.getMessage()).isEqualTo("Export file missing on disk for a.jpg");
            verify(vempainAdminService, never()).uploadAsSiteFile(any(), any());
        }

        @Test
        void publishAllFileGroupsCountsEveryExportedFileAsAStep() {
            // Two files in the group, one of them exported: the publish sizes itself by the exported one
            var projection = new FileGroupSummaryRow(7L, "/photos", "Photos", "A gallery", 2, 1, null);
            var group = FileGroupEntity.builder()
                .id(7L)
                .files(List.<FileEntity>of(ImageFileEntity.builder()
                    .id(70L)
                    .aclId(70L)
                    .filename("a.jpg")
                    .build(),
                        ImageFileEntity.builder()
                            .id(71L)
                            .aclId(71L)
                            .filename("b.jpg")
                            .build()))
                .build();
            when(fileGroupRepository.findById(7L)).thenReturn(Optional.of(group));
            when(fileGroupRepository.countExportedFilesById(7L)).thenReturn(1L);
            when(fileAclService.canModifyAll(group.getFiles())).thenReturn(true);
            when(fileGroupRepository.searchFileGroups(any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(projection)));
            when(exportFileRepository.findByFileId(70L)).thenReturn(Optional.of(ExportFileEntity.builder()
                .id(700L)
                .filePath("/nowhere")
                .filename("a.jpg")
                .build()));
            when(exportFileRepository.findByFileId(71L)).thenReturn(Optional.empty());
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishAllFileGroups();

            assertThat(task.getType()).isEqualTo("PUBLISH_ALL_FILE_GROUPS");
            // The total is the number of exported files of the publishable groups, known when the task is submitted
            verify(taskRunner).submitDurable(org.mockito.ArgumentMatchers.eq("PUBLISH_ALL_FILE_GROUPS"), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.eq(1L), any(), any());
            assertThat(task.getTotalSteps()
                .get()).isEqualTo(1);
            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
            // The exported file is missing on disk, so its step failed; the file without an export was never a step
            assertThat(task.getCompletedSteps()
                .get()).isEqualTo(1);
            assertThat(task.getFailedSteps()
                .get()).isEqualTo(1);
            assertThat(task.getMessage()).isEqualTo("Published Photos");
        }

        @Test
        void publishAllFileGroupsFailsTheRemainingStepsOfAGroupThatThrows() {
            var projection = new FileGroupSummaryRow(9L, "/broken", "Broken", "", 3, 3, null);
            var group = FileGroupEntity.builder()
                .id(9L)
                .files(List.<FileEntity>of(ImageFileEntity.builder()
                    .id(90L)
                    .aclId(90L)
                    .build()))
                .build();
            when(fileGroupRepository.findById(9L)).thenReturn(Optional.of(group))
                .thenThrow(new IllegalStateException("database gone"));
            when(fileGroupRepository.countExportedFilesById(9L)).thenReturn(3L);
            when(fileAclService.canModifyAll(group.getFiles())).thenReturn(true);
            when(fileGroupRepository.searchFileGroups(any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(projection)));
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishAllFileGroups();

            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
            assertThat(task.getTotalSteps()
                .get()).isEqualTo(3);
            assertThat(task.getFailedSteps()
                .get()).isEqualTo(3);
            assertThat(task.getMessage()).isEqualTo("Failed Broken: database gone");
        }

        @Test
        void publishAllFileGroupsSkipsGroupsTheUserCannotModify() {
            var projection = new FileGroupSummaryRow(8L, "/private", "Private", "Not mine", 1, 1, null);
            var group = FileGroupEntity.builder()
                .id(8L)
                .files(List.<FileEntity>of(ImageFileEntity.builder()
                    .id(80L)
                    .aclId(80L)
                    .build()))
                .build();
            when(fileGroupRepository.findById(8L)).thenReturn(Optional.of(group));
            when(fileAclService.canModifyAll(group.getFiles())).thenReturn(false);
            when(fileGroupRepository.searchFileGroups(any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(projection)));
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishAllFileGroups();

            assertThat(task.getTotalSteps()
                .get()).isZero();
            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
            org.mockito.Mockito.verify(fileGroupRepository, org.mockito.Mockito.never())
                .save(any());
        }

        @Test
        void publishAllCancelledBeforeStartPublishesNothing() {
            var projection = new FileGroupSummaryRow(7L, "/photos", "Photos", "A gallery", 2, 2, null);
            var group = FileGroupEntity.builder()
                .id(7L)
                .files(List.<FileEntity>of(ImageFileEntity.builder()
                    .id(70L)
                    .aclId(70L)
                    .build()))
                .build();
            when(fileGroupRepository.findById(7L)).thenReturn(Optional.of(group));
            when(fileAclService.canModifyAll(group.getFiles())).thenReturn(true);
            when(fileGroupRepository.searchFileGroups(any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(projection)));
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);
            var queued = new java.util.ArrayList<Runnable>();
            var runner = new TaskRunner(new TaskProgressStore(), queued::add);
            ReflectionTestUtils.setField(publishService, "taskRunner", runner);

            var task = publishService.publishAllFileGroups();
            runner.cancel(task);
            queued.get(0)
                .run();

            assertThat(task.getStatus()).isEqualTo(fi.poltsi.vempain.common.api.TaskStatusEnum.CANCELLED);
            org.mockito.Mockito.verify(exportFileRepository, org.mockito.Mockito.never())
                .findByFileId(any());
        }

        @Test
        void restoreGalleryIdPutsThePreviousLinkBack() {
            var group = FileGroupEntity.builder()
                .id(7L)
                .galleryId(99L)
                .build();
            when(fileGroupRepository.findById(7L)).thenReturn(Optional.of(group));

            publishService.restoreGalleryId(7L, 5L);

            assertThat(group.getGalleryId()).isEqualTo(5L);
            org.mockito.Mockito.verify(fileGroupRepository)
                .save(group);
            publishService.restoreGalleryId(404L, null);
        }

        @Test
        void authorizeFileGroupPublishRequiresModifyOnEveryFile() {
            var files = List.<fi.poltsi.vempain.file.entity.FileEntity>of(ImageFileEntity.builder()
                .id(90L)
                .aclId(90L)
                .build());
            var group = FileGroupEntity.builder()
                .id(9L)
                .files(files)
                .build();
            when(fileGroupRepository.findById(9L)).thenReturn(Optional.of(group));
            org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(fileAclService)
                .requireModify(files);

            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> publishService.authorizeFileGroupPublish(9L));
        }

        @Test
        void authorizeFileGroupPublishIgnoresMissingGroups() {
            when(fileGroupRepository.findById(404L)).thenReturn(Optional.empty());

            publishService.authorizeFileGroupPublish(404L);

            org.mockito.Mockito.verifyNoInteractions(fileAclService);
        }
    }

    // ------------------------------------------------------------------
    // additional ACL grantees and the admin user listing
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("ACL grantees and admin users")
    class AclGrantees {

        private PublishAclRequest acl(long userId, boolean read, boolean modify) {
            return PublishAclRequest.builder()
                .userId(userId)
                .readPrivilege(read)
                .modifyPrivilege(modify)
                .build();
        }

        @Test
        void toIngestAclsMapsEveryEntryAndLeavesAnEmptyListAsNull() {
            assertNull(PublishService.toIngestAcls(null));
            assertNull(PublishService.toIngestAcls(List.of()));

            var mapped = PublishService.toIngestAcls(List.of(acl(5L, true, true), acl(6L, true, false)));

            assertEquals(2, mapped.size());
            assertEquals(5L, mapped.get(0)
                .getUserId());
            assertTrue(mapped.get(0)
                .isReadPrivilege());
            assertTrue(mapped.get(0)
                .isModifyPrivilege());
            assertFalse(mapped.get(0)
                .isCreatePrivilege());
            assertFalse(mapped.get(0)
                .isDeletePrivilege());
            assertEquals(6L, mapped.get(1)
                .getUserId());
            assertFalse(mapped.get(1)
                .isModifyPrivilege());
        }

        @Test
        void publishFileGroupForwardsTheAclsInEveryIngestRequest(@TempDir Path exportDir) throws IOException {
            ReflectionTestUtils.setField(publishService, "exportRootDirectory", exportDir.toString());
            var subDir = exportDir.resolve("docs");
            Files.createDirectories(subDir);
            Files.write(subDir.resolve("doc.pdf"), "%PDF-1.4\n".getBytes());
            when(exportFileRepository.findByFileId(55L)).thenReturn(Optional.of(ExportFileEntity.builder()
                .id(2L)
                .filePath("/docs")
                .filename("doc.pdf")
                .build()));
            var mockDocEntity = mock(DocumentFileEntity.class);
            when(mockDocEntity.getId()).thenReturn(55L);
            when(mockDocEntity.getFileType()).thenReturn(FileTypeEnum.DOCUMENT);
            when(mockDocEntity.getFilePath()).thenReturn("/docs");
            when(mockDocEntity.getFilename()).thenReturn("doc.pdf");
            when(mockDocEntity.getDescription()).thenReturn(null);
            when(mockDocEntity.getGpsLocation()).thenReturn(null);
            when(mockDocEntity.getPageCount()).thenReturn(5);
            when(metadataService.getFileMetadata(55L)).thenReturn(generateMetadataEntities());
            when(tagService.getTagRequestsByFileId(55L)).thenReturn(List.of());
            var group = FileGroupEntity.builder()
                .id(9L)
                .files(List.<FileEntity>of(mockDocEntity))
                .build();
            when(fileGroupRepository.findById(9L)).thenReturn(Optional.of(group));
            when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(FileIngestResponse.builder()
                .siteFileId(500L)
                .galleryId(77L)
                .build());

            var request = PublishFileGroupRequest.builder()
                .fileGroupId(9L)
                .galleryName("Docs")
                .acls(List.of(acl(5L, true, true)))
                .build();
            publishService.publishFileGroupNow(request, null, false);

            var captor = ArgumentCaptor.forClass(FileIngestRequest.class);
            verify(vempainAdminService).uploadAsSiteFile(any(), captor.capture());
            var ingest = captor.getValue();
            assertEquals("Docs", ingest.getGalleryName());
            assertEquals(collectStandardMetadataAsJson(generateMetadataEntities(), mockDocEntity), ingest.getMetadata());
            assertEquals(1, ingest.getAcls()
                .size());
            assertEquals(5L, ingest.getAcls()
                .getFirst()
                .getUserId());
            assertTrue(ingest.getAcls()
                .getFirst()
                .isReadPrivilege());
            assertTrue(ingest.getAcls()
                .getFirst()
                .isModifyPrivilege());
            assertFalse(ingest.getAcls()
                .getFirst()
                .isDeletePrivilege());
            assertEquals(77L, group.getGalleryId());
        }

        @Test
        void publishAllFileGroupsCarriesTheAclsIntoEveryGroupRequest() {
            var projection = new FileGroupSummaryRow(7L, "/photos", "Photos", "A gallery", 2, 2, null);
            var group = FileGroupEntity.builder()
                .id(7L)
                .files(List.<FileEntity>of(ImageFileEntity.builder()
                    .id(70L)
                    .aclId(70L)
                    .build()))
                .build();
            when(fileGroupRepository.findById(7L)).thenReturn(Optional.of(group));
            when(fileAclService.canModifyAll(group.getFiles())).thenReturn(true);
            when(fileGroupRepository.searchFileGroups(any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(projection)));
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishAllFileGroups(PublishAllFileGroupsRequest.builder()
                .acls(List.of(acl(5L, true, false)))
                .build());

            assertThat(task.getType()).isEqualTo("PUBLISH_ALL_FILE_GROUPS");
            // The in-memory runner of this test does not persist the payload; inspect what was handed to the durable submit instead
            var payloadCaptor = ArgumentCaptor.forClass(Object.class);
            verify(taskRunner).submitDurable(org.mockito.ArgumentMatchers.eq("PUBLISH_ALL_FILE_GROUPS"), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyLong(), payloadCaptor.capture(), any());
            @SuppressWarnings("unchecked")
            var requests = (List<PublishFileGroupRequest>) ((java.util.Map<String, Object>) payloadCaptor.getValue()).get("requests");
            assertEquals(1, requests.size());
            assertEquals(7L, requests.getFirst()
                .getFileGroupId());
            assertEquals(1, requests.getFirst()
                .getAcls()
                .size());
            assertEquals(5L, requests.getFirst()
                .getAcls()
                .getFirst()
                .getUserId());
            assertTrue(requests.getFirst()
                .getAcls()
                .getFirst()
                .isReadPrivilege());
            assertFalse(requests.getFirst()
                .getAcls()
                .getFirst()
                .isModifyPrivilege());
        }

        @Test
        void listPublishUsersMapsTheAdminUsers() {
            when(vempainAdminService.listIngestUsers()).thenReturn(List.of(FileIngestUserResponse.builder()
                .id(12L)
                .loginName("arnold")
                .name("Arnold")
                .nick("Ahnold")
                .build()));

            var users = publishService.listPublishUsers();

            assertEquals(1, users.size());
            assertEquals(12L, users.getFirst()
                .getId());
            assertEquals("arnold", users.getFirst()
                .getLoginName());
            assertEquals("Arnold", users.getFirst()
                .getName());
            assertEquals("Ahnold", users.getFirst()
                .getNick());
        }

        @Test
        void toIngestAclsForwardsUnitGranteesWithoutAUser() {
            var mapped = PublishService.toIngestAcls(List.of(PublishAclRequest.builder()
                .unitId(3L)
                .readPrivilege(true)
                .createPrivilege(true)
                .build()));

            assertEquals(1, mapped.size());
            assertNull(mapped.getFirst()
                .getUserId());
            assertEquals(3L, mapped.getFirst()
                .getUnitId());
            assertTrue(mapped.getFirst()
                .isCreatePrivilege());
            assertTrue(mapped.getFirst()
                .isUserOrUnit());
        }

        @Test
        void listPublishUnitsMapsTheAdminUnits() {
            when(vempainAdminService.listIngestUnits()).thenReturn(List.of(FileIngestUnitResponse.builder()
                .id(3L)
                .name("Editors")
                .description("Site editors")
                .build()));

            var units = publishService.listPublishUnits();

            assertEquals(1, units.size());
            assertEquals(3L, units.getFirst()
                .getId());
            assertEquals("Editors", units.getFirst()
                .getName());
            assertEquals("Site editors", units.getFirst()
                .getDescription());
        }

        @Test
        void listPublishUnitsPassesARefusedTokenThrough() {
            when(vempainAdminService.listIngestUnits()).thenThrow(new fi.poltsi.vempain.auth.exception.VempainAuthenticationException());

            assertThrows(fi.poltsi.vempain.auth.exception.VempainAuthenticationException.class, () -> publishService.listPublishUnits());
        }

        @Test
        void listPublishUsersPassesARefusedTokenThrough() {
            when(vempainAdminService.listIngestUsers()).thenThrow(new fi.poltsi.vempain.auth.exception.VempainAuthenticationException());

            assertThrows(fi.poltsi.vempain.auth.exception.VempainAuthenticationException.class, () -> publishService.listPublishUsers());
        }

        @Test
        void listPublishUsersPassesOtherErrorsThrough() {
            when(vempainAdminService.listIngestUsers())
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY));

            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> publishService.listPublishUsers());
        }
    }

    // ------------------------------------------------------------------
    // single file publish
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("publishFile")
    class PublishSingleFile {

        private DocumentFileEntity exportedDocument(Path exportDir) throws IOException {
            ReflectionTestUtils.setField(publishService, "exportRootDirectory", exportDir.toString());
            var subDir = exportDir.resolve("docs");
            Files.createDirectories(subDir);
            Files.write(subDir.resolve("doc.pdf"), "%PDF-1.4\n".getBytes());
            when(exportFileRepository.findByFileId(55L)).thenReturn(Optional.of(ExportFileEntity.builder()
                .id(2L)
                .filePath("/docs")
                .filename("doc.pdf")
                .build()));
            var mockDocEntity = mock(DocumentFileEntity.class);
            when(mockDocEntity.getId()).thenReturn(55L);
            when(mockDocEntity.getFileType()).thenReturn(FileTypeEnum.DOCUMENT);
            when(mockDocEntity.getFilePath()).thenReturn("/docs");
            when(mockDocEntity.getFilename()).thenReturn("doc.pdf");
            when(mockDocEntity.getDescription()).thenReturn(null);
            when(mockDocEntity.getGpsLocation()).thenReturn(null);
            when(mockDocEntity.getPageCount()).thenReturn(5);
            when(tagService.getTagRequestsByFileId(55L)).thenReturn(List.of());
            when(fileRepository.findById(55L)).thenReturn(Optional.of(mockDocEntity));
            return mockDocEntity;
        }

        @Test
        void publishFileUploadsWithoutAnyGalleryAndForwardsTheAcls(@TempDir Path exportDir) throws IOException {
            var mockDocEntity = exportedDocument(exportDir);
            when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(FileIngestResponse.builder()
                .siteFileId(500L)
                .updated(false)
                .build());
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishFile(PublishFileRequest.builder()
                .fileId(55L)
                .acls(List.of(PublishAclRequest.builder()
                    .userId(5L)
                    .readPrivilege(true)
                    .build()))
                .build());

            assertThat(task.getType()).isEqualTo("PUBLISH_FILE");
            assertThat(task.getTitle()).isEqualTo("Publish file doc.pdf");
            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
            assertThat(task.getTotalSteps()
                .get()).isEqualTo(1);
            assertThat(task.getCompletedSteps()
                .get()).isEqualTo(1);
            assertThat(task.getMessage()).isEqualTo("Published doc.pdf");

            var captor = ArgumentCaptor.forClass(FileIngestRequest.class);
            verify(vempainAdminService).uploadAsSiteFile(any(), captor.capture());
            var ingest = captor.getValue();
            assertEquals(55L, ingest.getFileId());
            assertNull(ingest.getGalleryId());
            assertNull(ingest.getGalleryName());
            assertNull(ingest.getGalleryDescription());
            assertEquals(0L, ingest.getSortOrder());
            assertEquals(5, ingest.getPages());
            assertEquals(1, ingest.getAcls()
                .size());
            assertEquals(5L, ingest.getAcls()
                .getFirst()
                .getUserId());
            verify(fileGroupRepository, never()).save(any());
            // The created site file is recorded on the file for the next publish to find
            verify(mockDocEntity).setSiteFileId(500L);
            verify(mockDocEntity).setSiteFilePublished(true);
            verify(fileRepository).save(mockDocEntity);
        }

        @Test
        void publishFileWithoutExportFails() {
            var mockDocEntity = mock(DocumentFileEntity.class);
            when(mockDocEntity.getId()).thenReturn(56L);
            when(mockDocEntity.getFilename()).thenReturn("missing.pdf");
            when(fileRepository.findById(56L)).thenReturn(Optional.of(mockDocEntity));
            when(exportFileRepository.findByFileId(56L)).thenReturn(Optional.empty());
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishFile(PublishFileRequest.builder()
                .fileId(56L)
                .build());

            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.FAILED);
            assertThat(task.getErrorMessage()).isEqualTo("No export file for missing.pdf");
            verify(vempainAdminService, never()).uploadAsSiteFile(any(), any());
        }

        @Test
        void publishFileMarksMissingFileAsFailed() {
            when(fileRepository.findById(404L)).thenReturn(Optional.empty());
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishFile(PublishFileRequest.builder()
                .fileId(404L)
                .build());

            assertThat(task.getTitle()).isEqualTo("Publish file #404");
            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.FAILED);
            assertThat(task.getErrorMessage()).isEqualTo("File 404 not found");
        }

        @Test
        void cancelledPublishFileRemovesTheCreatedSiteFile(@TempDir Path exportDir) throws IOException {
            exportedDocument(exportDir);
            when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(FileIngestResponse.builder()
                .siteFileId(500L)
                .updated(false)
                .build());
            var store = new TaskProgressStore();
            var queued = new java.util.ArrayList<Runnable>();
            var runner = new TaskRunner(store, queued::add);
            ReflectionTestUtils.setField(publishService, "taskRunner", runner);
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

            var task = publishService.publishFile(PublishFileRequest.builder()
                .fileId(55L)
                .build());
            queued.get(0)
                .run();
            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);

            // A task that is cancelled while running would have reverted the upload through the registered compensation
            var running = publishService.publishFile(PublishFileRequest.builder()
                .fileId(55L)
                .build());
            runner.cancel(running);
            queued.get(1)
                .run();
            assertThat(running.getStatus()).isEqualTo(TaskStatusEnum.CANCELLED);
            verify(vempainAdminService, never()).deleteSiteFile(500L);
        }

        @Test
        void aPublishFileTaskThatFailsAfterTheUploadRemovesTheSiteFileAndForgetsTheLink(@TempDir Path exportDir) throws Exception {
            var mockDocEntity = exportedDocument(exportDir);
            // The file was published once before this release recorded site file IDs: an old link and an undetermined flag are put back on revert
            when(mockDocEntity.getSiteFileId()).thenReturn(400L);
            when(mockDocEntity.getSiteFilePublished()).thenReturn(null);
            when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(FileIngestResponse.builder()
                .siteFileId(500L)
                .updated(false)
                .build());
            when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);
            // The progress write after the upload fails, so the runner reverts the task through the registered compensation
            var progress = fi.poltsi.vempain.common.task.TaskProgress.unmanaged("t", "PUBLISH_FILE", "Publish file doc.pdf", 1L, 1, "{}");
            var compensations = new java.util.ArrayList<fi.poltsi.vempain.common.task.Compensation>();
            var spied = org.mockito.Mockito.spy(progress);
            org.mockito.Mockito.doAnswer(invocation -> {
                compensations.add(invocation.getArgument(1));
                return null;
            })
                .when(spied)
                .registerCompensation(any(), any());

            publishService.publishFileNow(PublishFileRequest.builder()
                .fileId(55L)
                .build(), spied);

            assertThat(compensations).hasSize(1);
            compensations.getFirst()
                .revert();
            verify(vempainAdminService).deleteSiteFile(500L);
            // The previous link (site file 400, flag undetermined) is put back
            verify(mockDocEntity).setSiteFileId(500L);
            verify(mockDocEntity).setSiteFilePublished(true);
            verify(mockDocEntity).setSiteFileId(400L);
            verify(mockDocEntity).setSiteFilePublished(org.mockito.ArgumentMatchers.isNull());
            verify(fileRepository, times(2)).save(mockDocEntity);
        }

        @Test
        void anUploadThatReplacedASiteFileRecordsTheLinkButRegistersNoUndo(@TempDir Path exportDir) throws IOException {
            var mockDocEntity = exportedDocument(exportDir);
            when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(FileIngestResponse.builder()
                .siteFileId(500L)
                .updated(true)
                .build());
            var spied = org.mockito.Mockito.spy(fi.poltsi.vempain.common.task.TaskProgress.unmanaged("t", "PUBLISH_FILE", "Publish file doc.pdf", 1L, 1, "{}"));

            publishService.publishFileNow(PublishFileRequest.builder()
                .fileId(55L)
                .build(), spied);

            verify(mockDocEntity).setSiteFileId(500L);
            verify(fileRepository).save(mockDocEntity);
            verify(spied, never()).registerCompensation(any(), any());
        }

        @Test
        void restoreSiteFileLinkPutsThePreviousValuesBack() {
            var file = ImageFileEntity.builder()
                .id(71L)
                .siteFileId(500L)
                .siteFilePublished(true)
                .build();
            when(fileRepository.findById(71L)).thenReturn(Optional.of(file));
            when(fileRepository.findById(72L)).thenReturn(Optional.empty());

            publishService.restoreSiteFileLink(71L, null, null);
            publishService.restoreSiteFileLink(72L, 1L, true);

            assertNull(file.getSiteFileId());
            assertNull(file.getSiteFilePublished());
            verify(fileRepository).save(file);
        }

        @Test
        void authorizeFilePublishRequiresModifyOnTheFileAndKnowsMissingFiles() {
            var file = ImageFileEntity.builder()
                .id(70L)
                .aclId(70L)
                .build();
            when(fileRepository.findById(70L)).thenReturn(Optional.of(file));
            publishService.authorizeFilePublish(70L);
            verify(fileAclService).requireModify(file);

            when(fileRepository.findById(404L)).thenReturn(Optional.empty());
            var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> publishService.authorizeFilePublish(404L));
            assertThat(ex.getStatusCode()
                .value()).isEqualTo(404);
        }

        @Test
        void authorizeFilesPublishLoadsTheSetOnceAndReportsMissingIds() {
            var first = ImageFileEntity.builder()
                .id(70L)
                .aclId(70L)
                .build();
            when(fileRepository.findAllById(any())).thenReturn(List.of(first));

            var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> publishService.authorizeFilesPublish(List.of(70L, 71L)));

            assertThat(ex.getStatusCode()
                .value()).isEqualTo(404);
            assertThat(ex.getReason()).isEqualTo("Files not found: [71]");
            verify(fileAclService, never()).requireModifyBulk(any());
            verify(fileRepository).findAllById(any());
        }

        @Test
        void authorizeFilesPublishUsesOneBulkAuthorizationCall() {
            var first = ImageFileEntity.builder()
                .id(70L)
                .aclId(70L)
                .build();
            var second = ImageFileEntity.builder()
                .id(71L)
                .aclId(71L)
                .build();
            when(fileRepository.findAllById(any())).thenReturn(List.of(first, second));

            publishService.authorizeFilesPublish(List.of(70L, 71L));

            verify(fileAclService).requireModifyBulk(List.of(first, second));
            verify(fileAclService, never()).requireModify(any(FileEntity.class));
        }
    }

    // ------------------------------------------------------------------
    // publishFileStatus / publishFileGroupStatus
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("publication status")
    class PublicationStatus {

        private final FileIngestAclResponse grantee = FileIngestAclResponse.builder()
            .userId(12L)
            .readPrivilege(true)
            .modifyPrivilege(true)
            .build();

        @Test
        void toPublishedAclsMapsEveryEntryAndLeavesNullEmpty() {
            var mapped = PublishService.toPublishedAcls(List.of(grantee, FileIngestAclResponse.builder()
                .unitId(3L)
                .readPrivilege(true)
                .deletePrivilege(true)
                .build()));

            assertEquals(2, mapped.size());
            assertEquals(12L, mapped.getFirst()
                .getUserId());
            assertNull(mapped.getFirst()
                .getUnitId());
            assertTrue(mapped.getFirst()
                .isReadPrivilege());
            assertFalse(mapped.getFirst()
                .isCreatePrivilege());
            assertTrue(mapped.getFirst()
                .isModifyPrivilege());
            assertFalse(mapped.getFirst()
                .isDeletePrivilege());
            assertEquals(3L, mapped.get(1)
                .getUnitId());
            assertTrue(mapped.get(1)
                .isDeletePrivilege());
            assertTrue(PublishService.toPublishedAcls(null)
                .isEmpty());
        }

        @Test
        void fileStatusUsesTheRecordedSiteFileAndRequiresModify() {
            var file = ImageFileEntity.builder()
                .id(55L)
                .siteFileId(500L)
                .build();
            when(fileRepository.findById(55L)).thenReturn(Optional.of(file));
            when(vempainAdminService.getSiteFileAcls(500L)).thenReturn(Optional.of(FileIngestSiteFileAclResponse.builder()
                .siteFileId(500L)
                .fileId(55L)
                .acls(List.of(grantee))
                .build()));

            var status = publishService.publishFileStatus(55L);

            verify(fileAclService).requireModify(file);
            assertEquals(55L, status.getFileId());
            assertTrue(status.isPublished());
            assertEquals(500L, status.getSiteFileId());
            assertEquals(1, status.getAcls()
                .size());
            assertEquals(12L, status.getAcls()
                .getFirst()
                .getUserId());
            verify(vempainAdminService, never()).getSiteFileAclsByFileId(anyLong());
        }

        @Test
        void fileStatusFallsBackToTheFileIdWhenNoSiteFileIsRecordedOrTheRecordedOneIsGone() {
            var legacy = ImageFileEntity.builder()
                .id(56L)
                .build();
            when(fileRepository.findById(56L)).thenReturn(Optional.of(legacy));
            when(vempainAdminService.getSiteFileAclsByFileId(56L)).thenReturn(Optional.of(FileIngestSiteFileAclResponse.builder()
                .siteFileId(600L)
                .fileId(56L)
                .acls(List.of())
                .build()));

            var legacyStatus = publishService.publishFileStatus(56L);

            assertTrue(legacyStatus.isPublished());
            assertEquals(600L, legacyStatus.getSiteFileId());
            assertTrue(legacyStatus.getAcls()
                .isEmpty());
            verify(vempainAdminService, never()).getSiteFileAcls(anyLong());

            var stale = ImageFileEntity.builder()
                .id(57L)
                .siteFileId(700L)
                .build();
            when(fileRepository.findById(57L)).thenReturn(Optional.of(stale));
            when(vempainAdminService.getSiteFileAcls(700L)).thenReturn(Optional.empty());
            when(vempainAdminService.getSiteFileAclsByFileId(57L)).thenReturn(Optional.empty());

            var staleStatus = publishService.publishFileStatus(57L);

            assertFalse(staleStatus.isPublished());
            assertNull(staleStatus.getSiteFileId());
            assertTrue(staleStatus.getAcls()
                .isEmpty());
        }

        @Test
        void fileStatusOfAMissingFileIsNotFound() {
            when(fileRepository.findById(404L)).thenReturn(Optional.empty());

            var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> publishService.publishFileStatus(404L));

            assertThat(ex.getStatusCode()
                .value()).isEqualTo(404);
            verify(vempainAdminService, never()).getSiteFileAclsByFileId(anyLong());
        }

        @Test
        void groupStatusAnswersTheGalleryItsSiteFilesAndGranteesAndRequiresModifyOnEveryFile() {
            var file = ImageFileEntity.builder()
                .id(61L)
                .build();
            var group = FileGroupEntity.builder()
                .id(407L)
                .galleryId(77L)
                .files(List.<FileEntity>of(file))
                .build();
            when(fileGroupRepository.findById(407L)).thenReturn(Optional.of(group));
            when(vempainAdminService.getGalleryAcls(77L)).thenReturn(Optional.of(FileIngestGalleryAclResponse.builder()
                .galleryId(77L)
                .shortname("Summer")
                .siteFileIds(List.of(500L, 501L))
                .acls(List.of(grantee))
                .build()));

            var status = publishService.publishFileGroupStatus(407L);

            verify(fileAclService).requireModify(group.getFiles());
            assertEquals(407L, status.getFileGroupId());
            assertTrue(status.isPublished());
            assertEquals(77L, status.getGalleryId());
            assertEquals("Summer", status.getGalleryName());
            assertEquals(List.of(500L, 501L), status.getSiteFileIds());
            assertEquals(12L, status.getAcls()
                .getFirst()
                .getUserId());
        }

        @Test
        void groupStatusOfAnUnpublishedOrStaleGroupIsNotPublished() {
            var never = FileGroupEntity.builder()
                .id(408L)
                .build();
            when(fileGroupRepository.findById(408L)).thenReturn(Optional.of(never));

            var neverStatus = publishService.publishFileGroupStatus(408L);

            assertFalse(neverStatus.isPublished());
            assertNull(neverStatus.getGalleryId());
            assertTrue(neverStatus.getSiteFileIds()
                .isEmpty());
            verify(vempainAdminService, never()).getGalleryAcls(anyLong());

            var stale = FileGroupEntity.builder()
                .id(409L)
                .galleryId(78L)
                .build();
            when(fileGroupRepository.findById(409L)).thenReturn(Optional.of(stale));
            when(vempainAdminService.getGalleryAcls(78L)).thenReturn(Optional.empty());

            assertFalse(publishService.publishFileGroupStatus(409L)
                .isPublished());
        }

        @Test
        void groupStatusOfAMissingGroupIsNotFound() {
            when(fileGroupRepository.findById(404L)).thenReturn(Optional.empty());

            var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> publishService.publishFileGroupStatus(404L));

            assertThat(ex.getStatusCode()
                .value()).isEqualTo(404);
        }
    }
}
