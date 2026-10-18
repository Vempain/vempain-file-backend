package fi.poltsi.vempain.file.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.MetadataEntity;

@Repository
public interface MetadataRepository extends JpaRepository<MetadataEntity, Long>, MetadataRepositoryCustom {

    List<MetadataEntity> findByFile(FileEntity file);

    /**
     * IDs of every file carrying exactly this group, key and value, in ascending order.
     */
    @Query("""
            SELECT DISTINCT m.file.id
            FROM MetadataEntity m
            WHERE m.metadataGroup = :metadataGroup AND m.metadataKey = :metadataKey AND m.metadataValue = :metadataValue
            ORDER BY m.file.id
            """)
    List<Long> findFileIdsByGroupKeyAndValue(@Param("metadataGroup") String metadataGroup, @Param("metadataKey") String metadataKey,
            @Param("metadataValue") String metadataValue);

    Optional<MetadataEntity> findFirstByFileIdAndMetadataGroupAndMetadataKey(long fileId, String metadataGroup, String metadataKey);

    List<MetadataEntity> findByFileId(long fileId);
}
