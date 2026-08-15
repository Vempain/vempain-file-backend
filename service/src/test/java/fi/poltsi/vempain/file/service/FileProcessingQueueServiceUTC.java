package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.FileProcessingQueueEntity;
import fi.poltsi.vempain.file.entity.FileProcessingStatus;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileProcessingQueueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileProcessingQueueServiceUTC {

	@Mock
	private FileProcessingQueueRepository queueRepository;
	@Mock
	private ExportFileRepository          exportFileRepository;
	@InjectMocks
	private FileProcessingQueueService    service;

	@Test
	void enqueueOnlyAddsUnexportedVideoWithoutExistingQueueItem() {
		var file = video(1L);
		when(exportFileRepository.findByFileId(1L)).thenReturn(Optional.empty());
		when(queueRepository.findByFileId(1L)).thenReturn(Optional.empty());

		service.enqueueIfNotExported(file);

		var captor = ArgumentCaptor.forClass(FileProcessingQueueEntity.class);
		verify(queueRepository).save(captor.capture());
		assertThat(captor.getValue()
		                 .getStatus()).isEqualTo(FileProcessingStatus.QUEUED);
		assertThat(captor.getValue()
		                 .getFile()).isSameAs(file);
	}

	@Test
	void enqueueSkipsNonVideoExportedAndQueuedFiles() {
		var image = new VideoFileEntity();
		image.setId(2L);
		image.setFileType(FileTypeEnum.IMAGE);
		service.enqueueIfNotExported(image);
		verifyNoInteractions(exportFileRepository, queueRepository);

		var file = video(3L);
		when(exportFileRepository.findByFileId(3L)).thenReturn(Optional.of(mock()));
		service.enqueueIfNotExported(file);
		verify(queueRepository, never()).save(any());

		when(exportFileRepository.findByFileId(3L)).thenReturn(Optional.empty());
		when(queueRepository.findByFileId(3L)).thenReturn(Optional.of(mock()));
		service.enqueueIfNotExported(file);
		verify(queueRepository, never()).save(any());
	}

	@Test
	void claimQueuedMarksItemsProcessingAndIncrementsAttempts() {
		var item = FileProcessingQueueEntity.builder()
		                                    .attempts(2)
		                                    .status(FileProcessingStatus.QUEUED)
		                                    .build();
		when(queueRepository.findByStatusOrderByCreatedAsc(eq(FileProcessingStatus.QUEUED), any())).thenReturn(List.of(item));
		when(queueRepository.saveAll(List.of(item))).thenReturn(List.of(item));

		assertThat(service.claimQueued(5)).containsExactly(item);
		assertThat(item.getStatus()).isEqualTo(FileProcessingStatus.PROCESSING);
		assertThat(item.getAttempts()).isEqualTo(3);
		assertThat(item.getStarted()).isNotNull();
		verify(queueRepository).saveAll(List.of(item));
	}

	@Test
	void completeAndFailPersistTerminalStatus() {
		var item = FileProcessingQueueEntity.builder()
		                                    .file(video(4L))
		                                    .fileType(FileTypeEnum.VIDEO)
		                                    .build();
		service.complete(item);
		assertThat(item.getStatus()).isEqualTo(FileProcessingStatus.COMPLETED);
		assertThat(item.getCompleted()).isNotNull();
		verify(queueRepository).save(item);

		var exception = new IllegalStateException("failed");
		service.fail(item, exception);
		assertThat(item.getStatus()).isEqualTo(FileProcessingStatus.FAILED);
		assertThat(item.getErrorMessage()).isEqualTo("failed");
		verify(queueRepository, times(2)).save(item);
	}

	private VideoFileEntity video(long id) {
		var file = new VideoFileEntity();
		file.setId(id);
		file.setFileType(FileTypeEnum.VIDEO);
		return file;
	}
}
