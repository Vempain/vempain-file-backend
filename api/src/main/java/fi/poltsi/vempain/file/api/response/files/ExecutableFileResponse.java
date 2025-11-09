package fi.poltsi.vempain.file.api.response.files;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Response DTO representing an executable file")
public class ExecutableFileResponse extends FileResponse {

	@Schema(description = "Operating systems supported by this executable", example = "[\"WINDOWS\",\"LINUX\"]")
	@NotNull
	private Set<String> operatingSystems;

	@Schema(description = "Whether the executable is a script (true) or a native/binary artifact (false)", example = "true")
	private boolean script;
}

