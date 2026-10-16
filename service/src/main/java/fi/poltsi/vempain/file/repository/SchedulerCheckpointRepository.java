package fi.poltsi.vempain.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.SchedulerCheckpointEntity;

@Repository
public interface SchedulerCheckpointRepository extends JpaRepository<SchedulerCheckpointEntity, String> {
}
