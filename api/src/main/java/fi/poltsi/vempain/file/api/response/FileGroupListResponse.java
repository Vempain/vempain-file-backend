package fi.poltsi.vempain.file.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "FileGroupResponse", description = "Represents a file group and the count of files, used to list the groups without loading all files")
@Tag(name = "FileGroupListResponse", description = "Schema for FileGroupListResponse")
public class FileGroupListResponse {

	@Schema(description = "ID of the file group", example = "123")
	private Long id;

	@Schema(description = "Common path of the file group", example = "/photos/2025/holiday")
	private String path;

	@Schema(description = "Name of the file group", example = "Firenze Trip 2025")
	private String groupName;

	@Schema(description = "Description of the file group", example = "Our trip to Firenze 2025 by car")
	private String description;

	@Schema(description = "How many files belong to this group", example = "42")
	private long fileCount;

	@Schema(description = "Potential ID of the published file group as gallery", example = "123")
	private Long galleryId;
}
