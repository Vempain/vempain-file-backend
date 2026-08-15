package fi.poltsi.vempain.file.schedule;

import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.FileProcessingQueueEntity;
import fi.poltsi.vempain.file.processing.FileQueueProcessor;
import fi.poltsi.vempain.file.processing.FileQueueProcessorFactory;
import fi.poltsi.vempain.file.service.FileProcessingQueueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class FileProcessingQueueScheduleUTC {

	@Mock
	private FileProcessingQueueService  queueService;
	@Mock
	private FileQueueProcessorFactory   processorFactory;
	@Mock
	private FileQueueProcessor          processor;
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
}
