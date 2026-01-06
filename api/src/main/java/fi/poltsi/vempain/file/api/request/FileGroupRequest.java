package fi.poltsi.vempain.file.api.request;

import fi.poltsi.vempain.file.api.AbstractFileGroup;
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
@Schema(name = "FileGroupRequest", description = "Represents a file group and its files")
@Tag(name = "FileGroups", description = "Schema for FileGroupRequest")
public class FileGroupRequest extends AbstractFileGroup {
	@Schema(description = "IDs of files to associate with the group; replaces existing associations on update", example = "[101,102,103]")
	@NotNull
	@Size(min = 0)
	private List<Long> fileIds;
}

