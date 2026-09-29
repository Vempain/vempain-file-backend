package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.FileProcessingQueueEntity;
import fi.poltsi.vempain.file.entity.FileProcessingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileProcessingQueueRepository extends JpaRepository<FileProcessingQueueEntity, Long> {
	Optional<FileProcessingQueueEntity> findByFileId(Long fileId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<FileProcessingQueueEntity> findByStatusOrderByCreatedAsc(FileProcessingStatus status, Pageable pageable);
}
