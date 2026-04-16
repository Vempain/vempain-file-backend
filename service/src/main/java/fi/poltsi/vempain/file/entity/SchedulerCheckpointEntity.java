package fi.poltsi.vempain.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "scheduler_checkpoint")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulerCheckpointEntity {

	@Id
	@Column(name = "task_name", nullable = false, length = 100)
	private String taskName;

	@Column(name = "last_checked", nullable = false)
	private Instant lastChecked;
}

