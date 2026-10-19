package fi.poltsi.vempain.file.metrics;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.FileProcessingStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The meters of the file backend carry the agreed names and bounded tags.
 */
class FileMetricsUTC {

    private SimpleMeterRegistry registry;
    private FileMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new FileMetrics(registry);
    }

    @Test
    void scanCountersAndTimerAreTaggedByDirectory() {
        var sample = metrics.scanStarted();
        metrics.scanned("original", 12, 3);
        metrics.scanFinished(sample, "original", true);

        assertThat(registry.get(FileMetrics.SCAN_FILES_SCANNED)
            .tag("directory", "original")
            .counter()
            .count()).isEqualTo(12);
        assertThat(registry.get(FileMetrics.SCAN_FILES_NEW)
            .tag("directory", "original")
            .counter()
            .count()).isEqualTo(3);
        assertThat(registry.get(FileMetrics.SCAN_DURATION)
            .tags("directory", "original", "result", "success")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void processingQueueOutcomesAndGaugeRows() {
        metrics.fileProcessingFinished(FileProcessingStatus.FAILED, FileTypeEnum.VIDEO, 3);
        metrics.fileProcessingFinished(FileProcessingStatus.COMPLETED, FileTypeEnum.VIDEO, 1);
        metrics.updateQueue(List.of(new FileMetrics.QueueCount(FileProcessingStatus.QUEUED, FileTypeEnum.VIDEO, 7)));

        assertThat(registry.get(FileMetrics.FILE_PROCESSING_ITEMS)
            .tags("result", "failed", "file_type", "VIDEO")
            .counter()
            .count()).isEqualTo(1);
        assertThat(registry.get(FileMetrics.FILE_PROCESSING_ATTEMPTS)
            .tag("file_type", "VIDEO")
            .summary()
            .totalAmount()).isEqualTo(4);
        assertThat(registry.get(FileMetrics.FILE_PROCESSING_QUEUE)
            .tags("status", "QUEUED", "file_type", "VIDEO")
            .gauge()
            .value()).isEqualTo(7);

        // A later refresh replaces the rows: the queued row is gone and the processing row is there
        metrics.updateQueue(List.of(new FileMetrics.QueueCount(FileProcessingStatus.PROCESSING, FileTypeEnum.VIDEO, 2)));
        assertThat(registry.find(FileMetrics.FILE_PROCESSING_QUEUE)
            .tags("status", "QUEUED")
            .gauge()).isNull();
        assertThat(registry.get(FileMetrics.FILE_PROCESSING_QUEUE)
            .tags("status", "PROCESSING")
            .gauge()
            .value()).isEqualTo(2);
    }

    @Test
    void publishResultsAndBytes() {
        metrics.published(FileMetrics.PublishResult.UPLOADED);
        metrics.published(FileMetrics.PublishResult.UPLOADED);
        metrics.published(FileMetrics.PublishResult.SKIPPED_NO_EXPORT);
        metrics.publishedBytes(1500);
        metrics.adminBackendRejected(403);

        assertThat(registry.get(FileMetrics.PUBLISH_FILES)
            .tag("result", "uploaded")
            .counter()
            .count()).isEqualTo(2);
        assertThat(registry.get(FileMetrics.PUBLISH_FILES)
            .tag("result", "skipped_no_export")
            .counter()
            .count()).isEqualTo(1);
        assertThat(registry.get(FileMetrics.PUBLISH_BYTES)
            .counter()
            .count()).isEqualTo(1500);
        assertThat(registry.get(FileMetrics.ADMIN_BACKEND_REJECTED)
            .tag("status", "403")
            .counter()
            .count()).isEqualTo(1);
    }

    @Test
    void scheduleCountersAndGauges() {
        metrics.thumbnailGenerated(true);
        metrics.thumbnailGenerated(false);
        metrics.setThumbnailsMissing(41);
        metrics.aclRepaired(5);
        metrics.refreshVerified();
        metrics.refreshRepublished();
        metrics.setRefreshCheckpointAgeSeconds(600);

        assertThat(registry.get(FileMetrics.THUMBNAILS_GENERATED)
            .tag("result", "failure")
            .counter()
            .count()).isEqualTo(1);
        assertThat(registry.get(FileMetrics.THUMBNAILS_MISSING)
            .gauge()
            .value()).isEqualTo(41);
        assertThat(registry.get(FileMetrics.ACL_REPAIRED)
            .counter()
            .count()).isEqualTo(5);
        assertThat(registry.get(FileMetrics.REFRESH_FILES_VERIFIED)
            .counter()
            .count()).isEqualTo(1);
        assertThat(registry.get(FileMetrics.REFRESH_FILES_REPUBLISHED)
            .counter()
            .count()).isEqualTo(1);
        assertThat(registry.get(FileMetrics.REFRESH_CHECKPOINT_AGE)
            .gauge()
            .value()).isEqualTo(600);
    }

    @Test
    void inventoryGaugesByFileType() {
        assertThat(registry.get(FileMetrics.REFRESH_CHECKPOINT_AGE)
            .gauge()
            .value()).as("no refresh has run yet")
            .isEqualTo(-1);

        metrics.updateInventory(List.of(new FileMetrics.FileTypeCount("IMAGE", 100), new FileMetrics.FileTypeCount("VIDEO", 4)), 90, 12, 8);

        assertThat(registry.get(FileMetrics.INVENTORY_FILES)
            .tag("file_type", "IMAGE")
            .gauge()
            .value()).isEqualTo(100);
        assertThat(registry.get(FileMetrics.INVENTORY_EXPORT_FILES)
            .gauge()
            .value()).isEqualTo(90);
        assertThat(registry.get(FileMetrics.INVENTORY_FILE_GROUPS)
            .gauge()
            .value()).isEqualTo(12);
        assertThat(registry.get(FileMetrics.INVENTORY_FILE_GROUPS_PUBLISHED)
            .gauge()
            .value()).isEqualTo(8);
    }
}
