package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.TaskCompensationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskCompensationRepository extends JpaRepository<TaskCompensationEntity, Long> {
	List<TaskCompensationEntity> findByTaskIdOrderByIdDesc(String taskId);

	void deleteByTaskId(String taskId);
}
