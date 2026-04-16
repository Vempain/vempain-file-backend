package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.SchedulerCheckpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SchedulerCheckpointRepository extends JpaRepository<SchedulerCheckpointEntity, String> {
}

