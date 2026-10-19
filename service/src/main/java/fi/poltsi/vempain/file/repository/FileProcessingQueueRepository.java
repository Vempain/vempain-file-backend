package fi.poltsi.vempain.file.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.FileProcessingQueueEntity;
import fi.poltsi.vempain.file.entity.FileProcessingStatus;

@Repository
public interface FileProcessingQueueRepository extends JpaRepository<FileProcessingQueueEntity, Long> {

    Optional<FileProcessingQueueEntity> findByFileId(Long fileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<FileProcessingQueueEntity> findByStatusOrderByCreatedAsc(FileProcessingStatus status, Pageable pageable);

    /**
     * Queue rows grouped by status and file type, for the queue gauge of the metrics.
     */
    @Query("SELECT q.status AS status, q.fileType AS fileType, COUNT(q) AS itemCount FROM FileProcessingQueueEntity q GROUP BY q.status, q.fileType")
    List<QueueStatusCountProjection> countByStatusAndFileType();

    interface QueueStatusCountProjection {

        FileProcessingStatus getStatus();

        FileTypeEnum getFileType();

        long getItemCount();
    }
}
