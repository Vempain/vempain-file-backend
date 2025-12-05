package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.FileGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileGroupRepository extends JpaRepository<FileGroupEntity, Long>, FileGroupRepositoryCustom {
	long countById(Long fileGroupId);

	Optional<FileGroupEntity> findByPathAndGroupName(String relativeDirectory, String groupName);
}
