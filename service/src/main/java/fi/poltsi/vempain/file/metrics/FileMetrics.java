package fi.poltsi.vempain.file.metrics;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.FileProcessingStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * The custom meters of the file backend in one place: the services report events through the methods of this component and the {@link FileMetricsRefresher}
 * feeds the gauges. Every name starts with {@code vempain.} and the tags are bounded enumerations, never ids or file names.
 */
@Component
public class FileMetrics {

    public static final String SCAN_FILES_SCANNED = "vempain.scan.files.scanned";
    public static final String SCAN_FILES_NEW = "vempain.scan.files.new";
    public static final String SCAN_DURATION = "vempain.scan.duration";
    public static final String FILE_PROCESSING_QUEUE = "vempain.file_processing.queue";
    public static final String FILE_PROCESSING_ATTEMPTS = "vempain.file_processing.attempts";
    public static final String FILE_PROCESSING_ITEMS = "vempain.file_processing.items";
    public static final String THUMBNAILS_GENERATED = "vempain.thumbnails.generated";
    public static final String THUMBNAILS_MISSING = "vempain.thumbnails.missing";
    public static final String PUBLISH_FILES = "vempain.publish.files";
    public static final String PUBLISH_BYTES = "vempain.publish.bytes";
    public static final String ADMIN_BACKEND_REJECTED = "vempain.admin_backend.rejected";
    public static final String ACL_REPAIRED = "vempain.acl.repaired";
    public static final String REFRESH_FILES_VERIFIED = "vempain.refresh.files.verified";
    public static final String REFRESH_FILES_REPUBLISHED = "vempain.refresh.files.republished";
    public static final String REFRESH_CHECKPOINT_AGE = "vempain.refresh.checkpoint.age.seconds";
    public static final String INVENTORY_FILES = "vempain.inventory.files";
    public static final String INVENTORY_EXPORT_FILES = "vempain.inventory.export_files";
    public static final String INVENTORY_FILE_GROUPS = "vempain.inventory.file_groups";
    public static final String INVENTORY_FILE_GROUPS_PUBLISHED = "vempain.inventory.file_groups.published";

    /** Publish outcomes, the {@code result} tag of {@link #PUBLISH_FILES}. */
    public enum PublishResult {

        UPLOADED,
        UPDATED,
        SKIPPED_NO_EXPORT,
        MISSING_ON_DISK,
        FAILED;

        String tagValue() {
            return name().toLowerCase();
        }
    }

    /** One row of the processing queue gauge: how many items of a type are in a status. */
    public record QueueCount(FileProcessingStatus status, FileTypeEnum fileType, long count) {}

    /** One row of the file inventory gauge. */
    public record FileTypeCount(String fileType, long count) {}

    private final MeterRegistry registry;
    private final MultiGauge queueGauge;
    private final MultiGauge inventoryFilesGauge;
    private final AtomicLong thumbnailsMissing = new AtomicLong();
    private final AtomicLong refreshCheckpointAgeSeconds = new AtomicLong(-1);
    private final AtomicLong exportFiles = new AtomicLong();
    private final AtomicLong fileGroups = new AtomicLong();
    private final AtomicLong fileGroupsPublished = new AtomicLong();

    public FileMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.queueGauge = MultiGauge.builder(FILE_PROCESSING_QUEUE)
            .description("Rows of the file processing queue by status and file type")
            .register(registry);
        this.inventoryFilesGauge = MultiGauge.builder(INVENTORY_FILES)
            .description("Files known to the file backend by type")
            .register(registry);
        Gauge.builder(THUMBNAILS_MISSING, thumbnailsMissing, AtomicLong::get)
            .description("Exported images and videos that still lack a thumbnail")
            .register(registry);
        Gauge.builder(REFRESH_CHECKPOINT_AGE, refreshCheckpointAgeSeconds, AtomicLong::get)
            .description("Seconds since the updated file refresh last completed; -1 before the first run")
            .register(registry);
        Gauge.builder(INVENTORY_EXPORT_FILES, exportFiles, AtomicLong::get)
            .description("Exported files known to the file backend")
            .register(registry);
        Gauge.builder(INVENTORY_FILE_GROUPS, fileGroups, AtomicLong::get)
            .description("File groups")
            .register(registry);
        Gauge.builder(INVENTORY_FILE_GROUPS_PUBLISHED, fileGroupsPublished, AtomicLong::get)
            .description("File groups linked to a gallery of the admin backend")
            .register(registry);
    }

    // Scan ------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * @param directory {@code original} or {@code export}
     */
    public void scanned(String directory, long scannedFiles, long newFiles) {
        counter(SCAN_FILES_SCANNED, "directory", directory).increment(scannedFiles);
        counter(SCAN_FILES_NEW, "directory", directory).increment(newFiles);
    }

    public Timer.Sample scanStarted() {
        return Timer.start(registry);
    }

    public void scanFinished(Timer.Sample sample, String directory, boolean success) {
        sample.stop(Timer.builder(SCAN_DURATION)
            .tags("directory", directory, "result", success ? ToolMetrics.RESULT_SUCCESS : ToolMetrics.RESULT_FAILURE)
            .register(registry));
    }

    // Processing queue ------------------------------------------------------------------------------------------------------------------------------

    /**
     * A queue item reached a final status after {@code attempts} tries.
     */
    public void fileProcessingFinished(FileProcessingStatus status, FileTypeEnum fileType, int attempts) {
        counter(FILE_PROCESSING_ITEMS, "result", status.name()
            .toLowerCase(), "file_type", fileType.name()).increment();
        DistributionSummary.builder(FILE_PROCESSING_ATTEMPTS)
            .description("Attempts a queue item needed before it reached a final status")
            .tag("file_type", fileType.name())
            .register(registry)
            .record(attempts);
    }

    public void updateQueue(List<QueueCount> counts) {
        queueGauge.register(counts.stream()
            .map(row -> MultiGauge.Row.of(Tags.of("status", row.status()
                .name(), "file_type",
                    row.fileType()
                        .name()),
                    row.count()))
            .toList(), true);
    }

    // Thumbnails ------------------------------------------------------------------------------------------------------------------------------------

    public void thumbnailGenerated(boolean success) {
        counter(THUMBNAILS_GENERATED, "result", success ? ToolMetrics.RESULT_SUCCESS : ToolMetrics.RESULT_FAILURE).increment();
    }

    public void setThumbnailsMissing(long count) {
        thumbnailsMissing.set(count);
    }

    // Publishing ------------------------------------------------------------------------------------------------------------------------------------

    public void published(PublishResult result) {
        counter(PUBLISH_FILES, "result", result.tagValue()).increment();
    }

    public void publishedBytes(long bytes) {
        counter(PUBLISH_BYTES).increment(bytes);
    }

    public void adminBackendRejected(int status) {
        counter(ADMIN_BACKEND_REJECTED, "status", Integer.toString(status)).increment();
    }

    // Schedules -------------------------------------------------------------------------------------------------------------------------------------

    public void aclRepaired(long count) {
        counter(ACL_REPAIRED).increment(count);
    }

    public void refreshVerified() {
        counter(REFRESH_FILES_VERIFIED).increment();
    }

    public void refreshRepublished() {
        counter(REFRESH_FILES_REPUBLISHED).increment();
    }

    public void setRefreshCheckpointAgeSeconds(long seconds) {
        refreshCheckpointAgeSeconds.set(seconds);
    }

    // Inventory -------------------------------------------------------------------------------------------------------------------------------------

    public void updateInventory(List<FileTypeCount> filesByType, long exportFileCount, long fileGroupCount, long publishedFileGroupCount) {
        inventoryFilesGauge.register(filesByType.stream()
            .map(row -> MultiGauge.Row.of(Tags.of("file_type", row.fileType()), row.count()))
            .toList(), true);
        exportFiles.set(exportFileCount);
        fileGroups.set(fileGroupCount);
        fileGroupsPublished.set(publishedFileGroupCount);
    }

    private Counter counter(String name, String... tags) {
        return registry.counter(name, tags);
    }
}
