package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.FileProcessingQueueEntity;
import fi.poltsi.vempain.file.entity.FileProcessingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileProcessingQueueRepository extends JpaRepository<FileProcessingQueueEntity, Long> {
	Optional<FileProcessingQueueEntity> findByFileId(Long fileId);

	List<FileProcessingQueueEntity> findByStatusOrderByCreatedAsc(FileProcessingStatus status, Pageable pageable);
}
