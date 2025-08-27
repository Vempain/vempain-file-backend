package fi.poltsi.vempain.file.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "FileGroupResponse", description = "Represents a file group and its files")
@Tag(name = "FileGroups", description = "Schema for FileGroupResponse")
public class FileGroupResponse {

	@Schema(description = "ID of the file group", example = "123")
	private Long id;

	@Schema(description = "Common path of the file group", example = "/photos/2025/holiday")
	private String path;

	@Schema(description = "Name of the file group", example = "Firenze Trip 2025")
	private String groupName;

	@Schema(description = "Files belonging to this group")
	private List<FileResponse> files;
}

