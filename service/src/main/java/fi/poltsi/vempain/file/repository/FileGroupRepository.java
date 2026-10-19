package fi.poltsi.vempain.file.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.FileGroupEntity;

@Repository
public interface FileGroupRepository extends JpaRepository<FileGroupEntity, Long>, FileGroupRepositoryCustom {

    long countById(Long fileGroupId);

    /**
     * Number of files in the group that have an exported file, without loading the collection; 0 for an unknown group. Only these files are transferred when
     * the group is published, so this is the count a publish works with.
     */
    @Query("SELECT COUNT(e) FROM ExportFileEntity e JOIN e.file f JOIN f.fileGroups g WHERE g.id = :fileGroupId")
    long countExportedFilesById(@Param("fileGroupId") long fileGroupId);

    Optional<FileGroupEntity> findByPathAndGroupName(String relativeDirectory, String groupName);

    /**
     * Groups that have been published as a gallery, for the inventory metrics.
     */
    long countByGalleryIdIsNotNull();
}
