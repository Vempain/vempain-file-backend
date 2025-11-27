package fi.poltsi.vempain.file.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enum representing publish progress status for a file group.
 */
@Schema(description = "Publish progress status for a file group",
		allowableValues = {"SCHEDULED", "STARTED", "COMPLETED", "FAILED"})
public enum PublishProgressStatusEnum {
	SCHEDULED,
	STARTED,
	COMPLETED,
	FAILED
}
