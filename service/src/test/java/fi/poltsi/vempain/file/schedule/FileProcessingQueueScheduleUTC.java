package fi.poltsi.vempain.file.schedule;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.FileProcessingQueueEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.processing.FileQueueProcessor;
import fi.poltsi.vempain.file.processing.FileQueueProcessorFactory;
import fi.poltsi.vempain.file.service.FileProcessingQueueService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class FileProcessingQueueScheduleUTC {

    @Mock
    private FileProcessingQueueService queueService;
    @Mock
    private FileQueueProcessorFactory processorFactory;
    @Mock
    private FileQueueProcessor processor;
    private FileProcessingQueueSchedule schedule;

    @AfterEach
    void stop() {
        if (schedule != null) {
            schedule.shutdownExecutor();
        }
    }

    @Test
    void processesClaimedItemsAndCompletesThem() throws Exception {
        schedule = new FileProcessingQueueSchedule(queueService, processorFactory);
        configure(true);
        var item = item();
        when(queueService.claimQueued(4)).thenReturn(List.of(item));
        when(processorFactory.processorFor(FileTypeEnum.VIDEO)).thenReturn(processor);

        schedule.processQueue();

        verify(processor).process(item);
        verify(queueService).complete(item);
    }

    @Test
    void failedProcessorMarksItemFailed() throws Exception {
        schedule = new FileProcessingQueueSchedule(queueService, processorFactory);
        configure(true);
        var item = item();
        when(queueService.claimQueued(4)).thenReturn(List.of(item));
        when(processorFactory.processorFor(FileTypeEnum.VIDEO)).thenReturn(processor);
        doThrow(new IllegalStateException("boom")).when(processor)
            .process(item);

        schedule.processQueue();

        verify(queueService).fail(eq(item), any(Exception.class));
        verify(queueService, never()).complete(item);
    }

    @Test
    void disabledScheduledRunDoesNothing() {
        schedule = new FileProcessingQueueSchedule(queueService, processorFactory);
        ReflectionTestUtils.setField(schedule, "enabled", false);

        schedule.processQueueScheduled();

        verifyNoInteractions(queueService, processorFactory);
    }

    private void configure(boolean enabled) {
        ReflectionTestUtils.setField(schedule, "enabled", enabled);
        ReflectionTestUtils.setField(schedule, "batchSize", 4);
        ReflectionTestUtils.setField(schedule, "workerCount", 2);
        schedule.initializeExecutor();
        assertThat(ReflectionTestUtils.getField(schedule, "executor")).isNotNull();
    }

    private FileProcessingQueueEntity item() {
        return FileProcessingQueueEntity.builder()
            .fileType(FileTypeEnum.VIDEO)
            .build();
    }

    @Test
    void scheduledRunProcessesTheQueueWhenEnabled() {
        schedule = new FileProcessingQueueSchedule(queueService, processorFactory);
        configure(true);
        when(queueService.claimQueued(4)).thenReturn(List.of());

        schedule.processQueueScheduled();

        verify(queueService).claimQueued(4);
    }

    @Test
    void concurrentInvocationIsSkipped() throws Exception {
        schedule = new FileProcessingQueueSchedule(queueService, processorFactory);
        configure(true);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(queueService.claimQueued(4)).thenAnswer(invocation -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return List.of();
        });

        var first = new Thread(schedule::processQueue);
        first.start();
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        schedule.processQueue();
        release.countDown();
        first.join(2000);

        verify(queueService).claimQueued(4);
    }

    @Test
    void interruptionWhileWaitingFailsTheItemAndRestoresTheFlag() throws Exception {
        schedule = new FileProcessingQueueSchedule(queueService, processorFactory);
        configure(true);
        var item = itemWithFile();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(queueService.claimQueued(4)).thenReturn(List.of(item));
        when(processorFactory.processorFor(FileTypeEnum.VIDEO)).thenReturn(processor);
        doAnswer(invocation -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return null;
        }).when(processor)
            .process(item);

        Thread.currentThread()
            .interrupt();
        boolean restored;
        try {
            schedule.processQueue();
        } finally {
            restored = Thread.interrupted();
            // the worker is still running the item; let it reach the processor before releasing it so the stubs are used
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
        }

        assertThat(restored).as("the interrupt flag is restored for the caller")
            .isTrue();
        verify(queueService).fail(eq(item), any(InterruptedException.class));
    }

    @Test
    void failureOfAnItemWithAFileIsWrappedAndReported() throws Exception {
        schedule = new FileProcessingQueueSchedule(queueService, processorFactory);
        configure(true);
        var item = itemWithFile();
        when(queueService.claimQueued(4)).thenReturn(List.of(item));
        when(processorFactory.processorFor(FileTypeEnum.VIDEO)).thenReturn(processor);
        doThrow(new IllegalStateException("encoder crashed")).when(processor)
            .process(item);

        schedule.processQueue();

        var failure = org.mockito.ArgumentCaptor.forClass(Exception.class);
        verify(queueService).fail(eq(item), failure.capture());
        assertThat(failure.getValue()).isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(IllegalStateException.class);
        verify(queueService, never()).complete(item);
    }

    private FileProcessingQueueEntity itemWithFile() {
        var file = new ImageFileEntity();
        file.setId(5L);
        return FileProcessingQueueEntity.builder()
            .id(50L)
            .file(file)
            .fileType(FileTypeEnum.VIDEO)
            .build();
    }
}
