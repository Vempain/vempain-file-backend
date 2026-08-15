package fi.poltsi.vempain.file.schedule;

import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.service.FileProcessingQueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueMissingVideoExportsScheduleUTC {
	@Mock
	private FileRepository                   fileRepository;
	@Mock
	private FileProcessingQueueService       queueService;
	@InjectMocks
	private QueueMissingVideoExportsSchedule schedule;

	@Test
	void queuesEveryVideoReturnedByTheMissingExportQuery() {
		var first  = video(1L);
		var second = video(2L);
		when(fileRepository.findVideosMissingExports(any(Pageable.class))).thenReturn(List.of(first, second));
		configure(25, 3, true);

		schedule.queueMissingVideoExports();

		verify(fileRepository).findVideosMissingExports(any(Pageable.class));
		verify(queueService).enqueueIfNotExported(first);
		verify(queueService).enqueueIfNotExported(second);
	}

	@Test
	void scheduledInvocationDoesNothingWhenDisabled() {
		configure(25, 3, false);

		schedule.queueMissingVideoExportsScheduled();

		verify(fileRepository, never()).findVideosMissingExports(any(Pageable.class));
		verify(queueService, never()).enqueueIfNotExported(any());
	}

	@Test
	void skipsConcurrentInvocation() throws Exception {
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		when(fileRepository.findVideosMissingExports(any(Pageable.class))).thenAnswer(invocation -> {
			entered.countDown();
			release.await();
			return List.of();
		});
		configure(25, 3, true);

		var firstRun = new Thread(schedule::queueMissingVideoExports);
		firstRun.start();
		assertThat(entered.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
		schedule.queueMissingVideoExports();
		release.countDown();
		firstRun.join(1000);

		verify(fileRepository).findVideosMissingExports(any(Pageable.class));
	}

	@Test
	void releasesRunningGuardAfterRepositoryFailure() {
		when(fileRepository.findVideosMissingExports(any(Pageable.class)))
				.thenThrow(new IllegalStateException("database unavailable"))
				.thenReturn(List.of());
		configure(25, 3, true);

		org.assertj.core.api.Assertions.assertThatThrownBy(schedule::queueMissingVideoExports)
									   .isInstanceOf(IllegalStateException.class);
		schedule.queueMissingVideoExports();

		verify(fileRepository, org.mockito.Mockito.times(2))
				.findVideosMissingExports(any(Pageable.class));
	}

	private void configure(int batchSize, int workerCount, boolean enabled) {
		ReflectionTestUtils.setField(schedule, "batchSize", batchSize);
		ReflectionTestUtils.setField(schedule, "workerCount", workerCount);
		ReflectionTestUtils.setField(schedule, "schedulerEnabled", enabled);
	}

	private FileEntity video(long id) {
		return VideoFileEntity.builder()
		                      .id(id)
		                      .build();
	}
}
