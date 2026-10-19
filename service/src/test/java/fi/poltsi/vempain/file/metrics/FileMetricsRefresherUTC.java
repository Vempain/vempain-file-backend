package fi.poltsi.vempain.file.metrics;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.FileProcessingStatus;
import fi.poltsi.vempain.file.entity.SchedulerCheckpointEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.FileProcessingQueueRepository;
import fi.poltsi.vempain.file.repository.SchedulerCheckpointRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.repository.files.FileTypeCountProjection;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The refresher reads the database once and pushes every gauge; a failing query keeps the previous values.
 */
@ExtendWith(MockitoExtension.class)
class FileMetricsRefresherUTC {

    @Mock
    private FileProcessingQueueRepository queueRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private ExportFileRepository exportFileRepository;
    @Mock
    private FileGroupRepository fileGroupRepository;
    @Mock
    private SchedulerCheckpointRepository schedulerCheckpointRepository;

    @Test
    void refreshFeedsEveryGauge() {
        var registry = new SimpleMeterRegistry();
        var metrics = new FileMetrics(registry);
        var refresher = new FileMetricsRefresher(metrics, queueRepository, fileRepository, exportFileRepository, fileGroupRepository,
                schedulerCheckpointRepository);
        when(queueRepository.countByStatusAndFileType()).thenReturn(List.of(queueRow(FileProcessingStatus.QUEUED, FileTypeEnum.VIDEO, 3)));
        when(fileRepository.countFilesByType()).thenReturn(List.of(typeRow("IMAGE", 10)));
        when(exportFileRepository.count()).thenReturn(9L);
        when(exportFileRepository.countImagesMissingThumbnails()).thenReturn(2L);
        when(fileGroupRepository.count()).thenReturn(4L);
        when(fileGroupRepository.countByGalleryIdIsNotNull()).thenReturn(1L);
        when(schedulerCheckpointRepository.findById(FileMetricsRefresher.REFRESH_TASK_NAME)).thenReturn(Optional.of(SchedulerCheckpointEntity.builder()
            .taskName(FileMetricsRefresher.REFRESH_TASK_NAME)
            .lastChecked(Instant.now()
                .minusSeconds(120))
            .build()));

        refresher.refresh();

        assertThat(registry.get(FileMetrics.FILE_PROCESSING_QUEUE)
            .tags("status", "QUEUED", "file_type", "VIDEO")
            .gauge()
            .value()).isEqualTo(3);
        assertThat(registry.get(FileMetrics.INVENTORY_FILES)
            .tag("file_type", "IMAGE")
            .gauge()
            .value()).isEqualTo(10);
        assertThat(registry.get(FileMetrics.INVENTORY_EXPORT_FILES)
            .gauge()
            .value()).isEqualTo(9);
        assertThat(registry.get(FileMetrics.THUMBNAILS_MISSING)
            .gauge()
            .value()).isEqualTo(2);
        assertThat(registry.get(FileMetrics.INVENTORY_FILE_GROUPS)
            .gauge()
            .value()).isEqualTo(4);
        assertThat(registry.get(FileMetrics.INVENTORY_FILE_GROUPS_PUBLISHED)
            .gauge()
            .value()).isEqualTo(1);
        assertThat(registry.get(FileMetrics.REFRESH_CHECKPOINT_AGE)
            .gauge()
            .value()).isBetween(119.0, 125.0);
    }

    @Test
    void refreshWithoutCheckpointReportsMinusOneAndAFailingQueryKeepsTheOldValues() {
        var registry = new SimpleMeterRegistry();
        var metrics = new FileMetrics(registry);
        var refresher = new FileMetricsRefresher(metrics, queueRepository, fileRepository, exportFileRepository, fileGroupRepository,
                schedulerCheckpointRepository);
        when(queueRepository.countByStatusAndFileType()).thenReturn(List.of());
        when(fileRepository.countFilesByType()).thenReturn(List.of());
        when(exportFileRepository.count()).thenReturn(5L);
        when(exportFileRepository.countImagesMissingThumbnails()).thenReturn(0L);
        when(fileGroupRepository.count()).thenReturn(0L);
        when(fileGroupRepository.countByGalleryIdIsNotNull()).thenReturn(0L);
        when(schedulerCheckpointRepository.findById(any())).thenReturn(Optional.empty());

        refresher.refresh();
        assertThat(registry.get(FileMetrics.REFRESH_CHECKPOINT_AGE)
            .gauge()
            .value()).isEqualTo(-1);
        assertThat(registry.get(FileMetrics.INVENTORY_EXPORT_FILES)
            .gauge()
            .value()).isEqualTo(5);

        when(queueRepository.countByStatusAndFileType()).thenThrow(new IllegalStateException("database gone"));
        refresher.refresh();
        assertThat(registry.get(FileMetrics.INVENTORY_EXPORT_FILES)
            .gauge()
            .value()).as("the failed refresh left the previous value")
            .isEqualTo(5);
    }

    private static FileProcessingQueueRepository.QueueStatusCountProjection queueRow(FileProcessingStatus status, FileTypeEnum type, long count) {
        return new FileProcessingQueueRepository.QueueStatusCountProjection() {

            @Override
            public FileProcessingStatus getStatus() {
                return status;
            }

            @Override
            public FileTypeEnum getFileType() {
                return type;
            }

            @Override
            public long getItemCount() {
                return count;
            }
        };
    }

    private static FileTypeCountProjection typeRow(String type, long count) {
        return new FileTypeCountProjection() {

            @Override
            public String getFileType() {
                return type;
            }

            @Override
            public Long getFileCount() {
                return count;
            }
        };
    }
}
