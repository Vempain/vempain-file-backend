package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.FileGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileGroupRepository extends JpaRepository<FileGroupEntity, Long> {

}
