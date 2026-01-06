package fi.poltsi.vempain.file.api.response;

import fi.poltsi.vempain.file.api.AbstractFileGroup;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "FileGroupResponse", description = "Represents a file group and its files")
@Tag(name = "FileGroups", description = "Schema for FileGroupResponse")
public class FileGroupResponse extends AbstractFileGroup {

	@Schema(description = "Files belonging to this group")
	@NotNull
	@Size(min = 0)
	private List<FileResponse> files;
}
