package fi.poltsi.vempain.file.api.response;

import fi.poltsi.vempain.file.api.PublishProgressStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Publishing progress information")
public class PublishProgressResponse {

	@Schema(description = "Total groups discovered for publishing", example = "10")
	private long totalGroups;

	@Schema(description = "How many groups were scheduled", example = "10")
	private long scheduled;

	@Schema(description = "How many groups started processing", example = "8")
	private long started;

	@Schema(description = "How many groups completed", example = "5")
	private long completed;

	@Schema(description = "How many groups failed", example = "1")
	private long failed;

	@Schema(description = "Per-group status map (groupId -> status)")
	private Map<Long, PublishProgressStatusEnum> perGroupStatus;

	@Schema(description = "When the progress was last updated")
	private Instant lastUpdated;
}
