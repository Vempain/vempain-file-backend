package fi.poltsi.vempain.file.schedule;

import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.service.ThumbnailGenerationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateMissingThumbnailsScheduleUTC {

	@Mock
	private ExportFileRepository       exportFileRepository;
	@Mock
	private ThumbnailGenerationService thumbnailGenerationService;
	@InjectMocks
	private GenerateMissingThumbnailsSchedule schedule;

	@AfterEach
	void stopExecutor() {
		schedule.shutdownExecutor();
	}

	@Test
	void usesConfiguredWorkerCountAndSubmitsFilesInParallel() throws Exception {
		var first   = exportFile(1L);
		var second  = exportFile(2L);
		var started = new CountDownLatch(2);
		var release = new CountDownLatch(1);
		when(exportFileRepository.findImagesMissingThumbnails(any())).thenReturn(List.of(first, second));
		doAnswer(invocation -> {
			started.countDown();
			if (started.getCount() == 0) {
				release.countDown();
			}
			release.await(2, TimeUnit.SECONDS);
			return null;
		}).when(thumbnailGenerationService)
		  .generateThumbnail(any(), any(), any(), any(Integer.class), any(Float.class));

		configure(2);
		schedule.generateMissingThumbnails();

		assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
		assertThat(((ThreadPoolTaskExecutor) ReflectionTestUtils.getField(schedule, "executor"))
						   .getCorePoolSize()).isEqualTo(2);
		verify(thumbnailGenerationService).generateThumbnail(first, null, null, 0, 0);
		verify(thumbnailGenerationService).generateThumbnail(second, null, null, 0, 0);
	}

	@Test
	void isolatesWorkerFailureFromOtherFiles() throws Exception {
		var failed     = exportFile(1L);
		var successful = exportFile(2L);
		when(exportFileRepository.findImagesMissingThumbnails(any())).thenReturn(List.of(failed, successful));
		doThrow(new IOException("conversion failed")).when(thumbnailGenerationService)
		                                             .generateThumbnail(failed, null, null, 0, 0);

		configure(2);
		schedule.generateMissingThumbnails();

		verify(thumbnailGenerationService).generateThumbnail(failed, null, null, 0, 0);
		verify(thumbnailGenerationService).generateThumbnail(successful, null, null, 0, 0);
	}

	private void configure(int workers) {
		ReflectionTestUtils.setField(schedule, "schedulerEnabled", true);
		ReflectionTestUtils.setField(schedule, "workerCount", workers);
		ReflectionTestUtils.setField(schedule, "batchSize", 10);
		schedule.initializeExecutor();
	}

	private ExportFileEntity exportFile(long id) {
		var target = new ImageFileEntity();
		target.setId(id);
		return ExportFileEntity.builder()
		                       .id(id)
		                       .file(target)
		                       .filename("image.jpg")
		                       .build();
	}
}
