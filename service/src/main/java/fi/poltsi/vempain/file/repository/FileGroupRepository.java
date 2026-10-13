package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.FileGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileGroupRepository extends JpaRepository<FileGroupEntity, Long>, FileGroupRepositoryCustom {
	long countById(Long fileGroupId);

	/**
	 * Number of files in the group without loading the collection; 0 for an unknown group.
	 */
	@Query("SELECT COUNT(f) FROM FileGroupEntity g JOIN g.files f WHERE g.id = :fileGroupId")
	long countFilesById(@Param("fileGroupId") long fileGroupId);

	Optional<FileGroupEntity> findByPathAndGroupName(String relativeDirectory, String groupName);
}
