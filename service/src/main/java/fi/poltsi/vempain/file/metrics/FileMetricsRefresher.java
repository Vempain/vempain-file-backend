package fi.poltsi.vempain.file.metrics;

import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.FileProcessingQueueRepository;
import fi.poltsi.vempain.file.repository.SchedulerCheckpointRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Feeds the gauges of {@link FileMetrics} from the database once a minute (configurable with {@code vempain.metrics.gauge-refresh-ms}), so that an export never
 * runs a query of its own.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileMetricsRefresher {

    static final String REFRESH_TASK_NAME = "updated_file_refresh";

    private final FileMetrics fileMetrics;
    private final FileProcessingQueueRepository queueRepository;
    private final FileRepository fileRepository;
    private final ExportFileRepository exportFileRepository;
    private final FileGroupRepository fileGroupRepository;
    private final SchedulerCheckpointRepository schedulerCheckpointRepository;

    @Scheduled(fixedDelayString = "${vempain.metrics.gauge-refresh-ms:60000}", initialDelayString = "${vempain.metrics.gauge-initial-delay-ms:15000}")
    @Transactional(readOnly = true)
    public void refresh() {
        try {
            fileMetrics.updateQueue(queueRepository.countByStatusAndFileType()
                .stream()
                .map(row -> new FileMetrics.QueueCount(row.getStatus(), row.getFileType(), row.getItemCount()))
                .toList());
            fileMetrics.updateInventory(fileRepository.countFilesByType()
                .stream()
                .map(row -> new FileMetrics.FileTypeCount(row.getFileType(), row.getFileCount()))
                .toList(), exportFileRepository.count(), fileGroupRepository.count(), fileGroupRepository.countByGalleryIdIsNotNull());
            fileMetrics.setThumbnailsMissing(exportFileRepository.countImagesMissingThumbnails());
            fileMetrics.setRefreshCheckpointAgeSeconds(schedulerCheckpointRepository.findById(REFRESH_TASK_NAME)
                .map(checkpoint -> Duration.between(checkpoint.getLastChecked(), Instant.now())
                    .toSeconds())
                .orElse(-1L));
        } catch (Exception e) {
            // A failed refresh keeps the previous values; the next run tries again
            log.warn("Could not refresh the file metrics gauges: {}", e.getMessage());
        }
    }
}
