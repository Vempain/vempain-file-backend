package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileProcessingQueueEntity;
import fi.poltsi.vempain.file.entity.FileProcessingStatus;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileProcessingQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingQueueService {
	private final FileProcessingQueueRepository queueRepository;
	private final ExportFileRepository          exportFileRepository;

	@Transactional
	public void enqueueIfNotExported(FileEntity file) {
		if (file.getFileType() != FileTypeEnum.VIDEO
			|| exportFileRepository.findByFileId(file.getId())
								   .isPresent()
			|| queueRepository.findByFileId(file.getId())
							  .isPresent()) {
			return;
		}

		var now = Instant.now();
		queueRepository.save(FileProcessingQueueEntity.builder()
													  .file(file)
													  .fileType(file.getFileType())
													  .status(FileProcessingStatus.QUEUED)
													  .created(now)
													  .updated(now)
													  .build());
		log.info("Queued video file id={} for export", file.getId());
	}

	@Transactional
	public List<FileProcessingQueueEntity> claimQueued(int batchSize) {
		var queued = queueRepository.findByStatusOrderByCreatedAsc(
				FileProcessingStatus.QUEUED, PageRequest.of(0, batchSize));
		var now = Instant.now();
		queued.forEach(item -> {
			item.setStatus(FileProcessingStatus.PROCESSING);
			item.setStarted(now);
			item.setUpdated(now);
			item.setAttempts(item.getAttempts() + 1);
		});
		return queueRepository.saveAll(queued);
	}

	@Transactional
	public void complete(FileProcessingQueueEntity item) {
		updateStatus(item, FileProcessingStatus.COMPLETED, null);
	}

	@Transactional
	public void fail(FileProcessingQueueEntity item, Exception exception) {
		var message = exception.getMessage() == null ? exception.getClass()
																.getName() : exception.getMessage();
		log.error("Failed to process file queue item id={} fileId={} type={}: {}",
				  item.getId(), item.getFile()
									.getId(), item.getFileType(), message, exception);
		updateStatus(item, FileProcessingStatus.FAILED, message);
	}

	private void updateStatus(FileProcessingQueueEntity item, FileProcessingStatus status, String error) {
		item.setStatus(status);
		item.setUpdated(Instant.now());
		item.setCompleted(status == FileProcessingStatus.COMPLETED ? Instant.now() : null);
		item.setErrorMessage(error);
		queueRepository.save(item);
	}
}
