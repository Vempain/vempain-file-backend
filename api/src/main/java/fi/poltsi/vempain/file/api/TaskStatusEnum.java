package fi.poltsi.vempain.file.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lifecycle states of a background task tracked by the task progress facility.
 */
@Schema(description = "Lifecycle state of a background task", allowableValues = {"QUEUED", "RUNNING", "COMPLETED", "FAILED"})
public enum TaskStatusEnum {
	QUEUED,
	RUNNING,
	COMPLETED,
	FAILED;

	public boolean isFinished() {
		return this == COMPLETED || this == FAILED;
	}
}
