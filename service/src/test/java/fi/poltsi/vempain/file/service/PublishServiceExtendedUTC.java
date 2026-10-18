package fi.poltsi.vempain.file.service;

import java.awt.Dimension;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.admin.api.response.file.FileIngestResponse;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.common.api.TaskStatusEnum;
import fi.poltsi.vempain.common.task.TaskProgress;
import fi.poltsi.vempain.common.task.TaskProgressStore;
import fi.poltsi.vempain.common.task.TaskRunner;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.request.PublishFileRequest;
import fi.poltsi.vempain.file.api.request.PublishFilesRequest;
import fi.poltsi.vempain.file.entity.AudioFileEntity;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.entity.GpsLocationEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepositoryCustom.FileGroupSummaryRow;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.tools.ImageTool;
import fi.poltsi.vempain.file.tools.SampleFiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (UTC) for the upload details of {@link PublishService}: the multi file publish task, image resizing, media lengths, guarded locations, the
 * compensations of a failed group publish and cancellation between groups.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublishServiceExtendedUTC {

    private static final int MAX_IMAGE_SIZE = 1200;

    @TempDir
    Path exportDir;
    @Mock
    private FileGroupRepository fileGroupRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private ExportFileRepository exportFileRepository;
    @Mock
    private VempainAdminService vempainAdminService;
    @Mock
    private MetadataService metadataService;
    @Mock
    private TagService tagService;
    @Mock
    private LocationService locationService;
    @Mock
    private FileAclService fileAclService;
    @Mock
    private ImageTool imageTool;
    @Mock
    private ApplicationContext applicationContext;
    @Spy
    private TaskRunner taskRunner = new TaskRunner(new TaskProgressStore(), Runnable::run);
    @InjectMocks
    private PublishService publishService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publishService, "exportRootDirectory", exportDir.toString());
        ReflectionTestUtils.setField(publishService, "exportFileType", "jpeg");
        ReflectionTestUtils.setField(publishService, "siteImageSize", MAX_IMAGE_SIZE);
        when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);
        when(tagService.getTagRequestsByFileId(any(Long.class))).thenReturn(List.of());
        when(exportFileRepository.findByFileId(any())).thenReturn(Optional.empty());
    }

    private <T extends FileEntity> T exported(T entity, long id, FileTypeEnum type, String filename, String filePath) throws Exception {
        entity.setId(id);
        entity.setFileType(type);
        entity.setFilename(filename);
        entity.setFilePath(filePath);
        SampleFiles.jpeg(exportDir.resolve(filename));
        when(exportFileRepository.findByFileId(id)).thenReturn(Optional.of(ExportFileEntity.builder()
            .id(id * 10)
            .file(entity)
            .filePath("/")
            .filename(filename)
            .build()));
        when(fileRepository.findById(id)).thenReturn(Optional.of(entity));
        return entity;
    }

    private static FileIngestResponse created(long siteFileId, Long galleryId) {
        return FileIngestResponse.builder()
            .siteFileId(siteFileId)
            .galleryId(galleryId)
            .updated(false)
            .build();
    }

    @Test
    void publishFilesResizesImagesAndReportsFilesWithoutExportAsFailedSteps() throws Exception {
        var image = exported(new ImageFileEntity(), 1L, FileTypeEnum.IMAGE, "a.jpg", "/image/holiday");
        when(fileRepository.findById(2L)).thenReturn(Optional.of(ImageFileEntity.builder()
            .id(2L)
            .fileType(FileTypeEnum.IMAGE)
            .filename("b.jpg")
            .filePath("/image/holiday")
            .build()));
        when(imageTool.resizeImage(any(), any(), eq(MAX_IMAGE_SIZE), eq(0.7f), anyString())).thenAnswer(invocation -> {
            Files.copy(invocation.getArgument(0, Path.class), invocation.getArgument(1, Path.class), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return new Dimension(MAX_IMAGE_SIZE, 800);
        });
        when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(created(10L, null));
        when(metadataService.getFileMetadata(anyLong())).thenReturn(List.of());

        var task = publishService.publishFiles(PublishFilesRequest.builder()
            .fileIds(List.of(1L, 2L))
            .build());

        assertThat(task.getType()).isEqualTo("PUBLISH_FILES");
        assertThat(task.getTitle()).isEqualTo("Publish selected files");
        assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
        assertThat(task.getCompletedSteps()
            .get()).isEqualTo(2);
        assertThat(task.getFailedSteps()
            .get()).isEqualTo(1);
        var captor = ArgumentCaptor.forClass(FileIngestRequest.class);
        verify(vempainAdminService).uploadAsSiteFile(any(), captor.capture());
        var ingest = captor.getValue();
        assertThat(ingest.getFileId()).isEqualTo(1L);
        assertThat(ingest.getFileName()).isEqualTo("a.jpeg");
        assertThat(ingest.getFilePath()).isEqualTo("holiday");
        assertThat(ingest.getWidth()).isEqualTo(MAX_IMAGE_SIZE);
        assertThat(ingest.getHeight()).isEqualTo(800);
        assertThat(ingest.getMimeType()).isEqualTo("image/jpeg");
        assertThat(ingest.getComment()).isEmpty();
        assertThat(image.getFilename()).isEqualTo("a.jpg");
    }

    @Test
    void mediaLengthsPagesAndLocationsAreForwardedAndGuardedLocationsAreWithheld() throws Exception {
        var video = exported(new VideoFileEntity(), 3L, FileTypeEnum.VIDEO, "clip.mp4", "/video/x");
        video.setDuration(Duration.ofSeconds(90));
        var guarded = new GpsLocationEntity();
        guarded.setLatitude(new BigDecimal("60.1"));
        video.setGpsLocation(guarded);
        when(locationService.isGuardedLocation(guarded)).thenReturn(true);
        var audio = exported(new AudioFileEntity(), 4L, FileTypeEnum.AUDIO, "tone.wav", null);
        audio.setDuration(Duration.ofSeconds(30));
        var open = new GpsLocationEntity();
        open.setId(8L);
        open.setLatitude(new BigDecimal("60.17"));
        open.setLatitudeRef('N');
        open.setLongitude(new BigDecimal("24.94"));
        open.setLongitudeRef('E');
        audio.setGpsLocation(open);
        when(locationService.isGuardedLocation(open)).thenReturn(false);
        when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(null);

        publishService.publishFileNow(PublishFileRequest.builder()
            .fileId(3L)
            .build(), null);
        publishService.publishFileNow(PublishFileRequest.builder()
            .fileId(4L)
            .build(), null);

        var captor = ArgumentCaptor.forClass(FileIngestRequest.class);
        verify(vempainAdminService, times(2)).uploadAsSiteFile(any(), captor.capture());
        var videoIngest = captor.getAllValues()
            .get(0);
        assertThat(videoIngest.getFileId()).isEqualTo(3L);
        assertThat(videoIngest.getLength()).isEqualTo(Duration.ofSeconds(90));
        assertThat(videoIngest.getLocation()).isNull();
        assertThat(videoIngest.getFilePath()).isEqualTo("x");
        var audioIngest = captor.getAllValues()
            .get(1);
        assertThat(audioIngest.getFileId()).isEqualTo(4L);
        assertThat(audioIngest.getLength()).isEqualTo(Duration.ofSeconds(30));
        assertThat(audioIngest.getLocation()).isNotNull();
        assertThat(audioIngest.getLocation()
            .getLatitude()).isEqualByComparingTo(new BigDecimal("60.17"));
        assertThat(audioIngest.getFilePath()).isEmpty();
        verify(imageTool, never()).resizeImage(any(), any(), any(Integer.class), any(Float.class), any());
    }

    @Test
    void uploadsThatReplacedOrReturnedNothingRegisterNoCompensation() throws Exception {
        exported(new AudioFileEntity(), 5L, FileTypeEnum.AUDIO, "five.wav", "/");
        var progress = TaskProgress.unmanaged("t", "PUBLISH_FILE", "title", 1L, 2, "{}");
        when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(FileIngestResponse.builder()
            .siteFileId(1L)
            .updated(true)
            .build(),
                FileIngestResponse.builder()
                    .build());

        publishService.publishFileNow(PublishFileRequest.builder()
            .fileId(5L)
            .build(), progress);
        publishService.publishFileNow(PublishFileRequest.builder()
            .fileId(5L)
            .build(), progress);

        assertThat(progress.getCompensations()).isEmpty();
        assertThat(progress.getCompletedSteps()
            .get()).isEqualTo(2);
    }

    @Test
    void aFailingGroupPublishRemovesTheCreatedSiteFileAndRestoresTheGalleryLink() throws Exception {
        var good = exported(new AudioFileEntity(), 6L, FileTypeEnum.AUDIO, "good.jpg", "/");
        var bad = exported(new AudioFileEntity(), 7L, FileTypeEnum.AUDIO, "bad.jpg", "/");
        var group = FileGroupEntity.builder()
            .id(7L)
            .groupName("Trip")
            .files(new ArrayList<>(List.of(good, bad)))
            .build();
        when(fileGroupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(fileGroupRepository.countFilesById(7L)).thenReturn(2L);
        when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(created(500L, 55L))
            .thenThrow(new IllegalStateException("admin refused"));
        when(fileGroupRepository.save(group)).thenThrow(new IllegalStateException("database gone"))
            .thenReturn(group);

        var task = publishService.publishFileGroup(PublishFileGroupRequest.builder()
            .fileGroupId(7L)
            .galleryName("Trip")
            .build());

        assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.FAILED);
        assertThat(task.getErrorMessage()).isEqualTo("database gone");
        assertThat(task.getFailedSteps()
            .get()).isEqualTo(1);
        assertThat(task.getRevertedSteps()
            .get()).isEqualTo(2);
        assertThat(group.getGalleryId()).as("the gallery link was restored to its previous value")
            .isNull();
        verify(vempainAdminService).deleteSiteFile(500L);
        verify(fileGroupRepository, times(2)).save(group);
    }

    @Test
    void cancellingWhileAGroupPublishesStopsThePublishAllTask() throws Exception {
        var first = exported(new AudioFileEntity(), 8L, FileTypeEnum.AUDIO, "first.jpg", "/");
        var second = exported(new AudioFileEntity(), 9L, FileTypeEnum.AUDIO, "second.jpg", "/");
        var groupA = FileGroupEntity.builder()
            .id(1L)
            .groupName("A")
            .files(new ArrayList<>(List.of(first)))
            .build();
        var groupB = FileGroupEntity.builder()
            .id(2L)
            .groupName("B")
            .files(new ArrayList<>(List.of(second)))
            .build();
        when(fileGroupRepository.searchFileGroups(any(), anyBoolean(), any()))
            .thenReturn(new PageImpl<>(List.of(new FileGroupSummaryRow(1L, "/a", "A", "", 1, null), new FileGroupSummaryRow(2L, "/b", "B", "", 1, null))));
        when(fileGroupRepository.findById(1L)).thenReturn(Optional.of(groupA));
        when(fileGroupRepository.findById(2L)).thenReturn(Optional.of(groupB));
        when(fileGroupRepository.countFilesById(org.mockito.ArgumentMatchers.anyLong())).thenReturn(1L);
        when(fileAclService.canModifyAll(any())).thenReturn(true);
        var queued = new ArrayList<Runnable>();
        var runner = new TaskRunner(new TaskProgressStore(), queued::add);
        ReflectionTestUtils.setField(publishService, "taskRunner", runner);
        var task = new AtomicReference<TaskProgress>();
        // The owner cancels while the first group is being uploaded; the next checkpoint stops the task
        when(vempainAdminService.uploadAsSiteFile(any(), any())).thenAnswer(invocation -> {
            runner.cancel(task.get());
            return created(600L, 66L);
        });

        task.set(publishService.publishAllFileGroups());
        queued.getFirst()
            .run();

        assertThat(task.get()
            .getStatus()).isEqualTo(TaskStatusEnum.CANCELLED);
        verify(vempainAdminService).uploadAsSiteFile(any(), any());
        verify(vempainAdminService).deleteSiteFile(600L);
        verify(exportFileRepository, never()).findByFileId(9L);
    }

    @Test
    void republishReturnsFalseWhenTheUploadFails() throws Exception {
        var file = exported(new AudioFileEntity(), 11L, FileTypeEnum.AUDIO, "eleven.wav", "/audio/x");
        when(vempainAdminService.uploadAsSiteFile(any(), any())).thenThrow(new IllegalStateException("admin down"));

        assertThat(publishService.republishSiteFile(file)).isFalse();
    }

    @Test
    void ingestPathsAreRelativeAndStrippedOfTheTypePrefix() throws Exception {
        var prefixed = exported(new AudioFileEntity(), 12L, FileTypeEnum.AUDIO, "p.wav", "/audio/2024/trip");
        var plain = exported(new AudioFileEntity(), 13L, FileTypeEnum.AUDIO, "q.wav", "other/2024");
        when(vempainAdminService.uploadAsSiteFile(any(), any())).thenReturn(null);

        publishService.republishSiteFile(prefixed);
        publishService.republishSiteFile(plain);

        var captor = ArgumentCaptor.forClass(FileIngestRequest.class);
        verify(vempainAdminService, times(2)).uploadAsSiteFile(any(), captor.capture());
        assertThat(captor.getAllValues()).extracting(FileIngestRequest::getFilePath)
            .containsExactly("2024/trip", "other/2024");
    }
}
