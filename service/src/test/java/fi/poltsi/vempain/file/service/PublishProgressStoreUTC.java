package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.PublishProgressStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests (UTC) for {@link PublishProgressStore}.
 */
class PublishProgressStoreUTC {

    private PublishProgressStore store;

    @BeforeEach
    void setup() {
        store = new PublishProgressStore();
    }

    @Test
    @DisplayName("init resets all counters and sets total")
    void init_resetsCounters() {
        store.init(10L);
        assertThat(store.getTotal()).isEqualTo(10L);
        assertThat(store.getScheduled()).isEqualTo(0L);
        assertThat(store.getStarted()).isEqualTo(0L);
        assertThat(store.getCompleted()).isEqualTo(0L);
        assertThat(store.getFailed()).isEqualTo(0L);
    }

    @Test
    @DisplayName("init clears per-group status map")
    void init_clearsPerGroupMap() {
        store.init(5L);
        store.markScheduled(1L);
        store.init(3L);
        assertThat(store.getPerGroupStatus()).isEmpty();
    }

    @Test
    @DisplayName("markScheduled increments scheduled and sets per-group status")
    void markScheduled_incrementsAndSetsStatus() {
        store.init(3L);
        store.markScheduled(1L);
        store.markScheduled(2L);

        assertThat(store.getScheduled()).isEqualTo(2L);
        assertThat(store.getPerGroupStatus().get(1L)).isEqualTo(PublishProgressStatusEnum.SCHEDULED);
        assertThat(store.getPerGroupStatus().get(2L)).isEqualTo(PublishProgressStatusEnum.SCHEDULED);
    }

    @Test
    @DisplayName("markStarted increments started and sets per-group status")
    void markStarted_incrementsAndSetsStatus() {
        store.init(3L);
        store.markScheduled(1L);
        store.markStarted(1L);

        assertThat(store.getStarted()).isEqualTo(1L);
        assertThat(store.getPerGroupStatus().get(1L)).isEqualTo(PublishProgressStatusEnum.STARTED);
    }

    @Test
    @DisplayName("markCompleted increments completed and sets per-group status")
    void markCompleted_incrementsAndSetsStatus() {
        store.init(3L);
        store.markScheduled(1L);
        store.markStarted(1L);
        store.markCompleted(1L);

        assertThat(store.getCompleted()).isEqualTo(1L);
        assertThat(store.getPerGroupStatus().get(1L)).isEqualTo(PublishProgressStatusEnum.COMPLETED);
    }

    @Test
    @DisplayName("markFailed increments failed and sets per-group status")
    void markFailed_incrementsAndSetsStatus() {
        store.init(3L);
        store.markScheduled(2L);
        store.markFailed(2L);

        assertThat(store.getFailed()).isEqualTo(1L);
        assertThat(store.getPerGroupStatus().get(2L)).isEqualTo(PublishProgressStatusEnum.FAILED);
    }

    @Test
    @DisplayName("lastUpdated is updated on each operation")
    void lastUpdated_updatesOnOperation() throws InterruptedException {
        store.init(1L);
        var after = store.getLastUpdated();
        assertThat(after).isNotNull();

        Thread.sleep(5);
        store.markScheduled(1L);
        assertThat(store.getLastUpdated()).isAfterOrEqualTo(after);
    }

    @Test
    @DisplayName("getPerGroupStatus returns all tracked group statuses")
    void getPerGroupStatus_returnsAllStatuses() {
        store.init(3L);
        store.markScheduled(1L);
        store.markStarted(2L);
        store.markCompleted(3L);

        var status = store.getPerGroupStatus();
        assertThat(status).hasSize(3);
        assertThat(status.get(1L)).isEqualTo(PublishProgressStatusEnum.SCHEDULED);
        assertThat(status.get(2L)).isEqualTo(PublishProgressStatusEnum.STARTED);
        assertThat(status.get(3L)).isEqualTo(PublishProgressStatusEnum.COMPLETED);
    }

    @Test
    @DisplayName("multiple operations accumulate correctly")
    void multipleOperations_accumulateCorrectly() {
        store.init(5L);
        store.markScheduled(1L);
        store.markScheduled(2L);
        store.markScheduled(3L);
        store.markStarted(1L);
        store.markStarted(2L);
        store.markCompleted(1L);
        store.markFailed(3L);

        assertThat(store.getTotal()).isEqualTo(5L);
        assertThat(store.getScheduled()).isEqualTo(3L);
        assertThat(store.getStarted()).isEqualTo(2L);
        assertThat(store.getCompleted()).isEqualTo(1L);
        assertThat(store.getFailed()).isEqualTo(1L);
    }
}
