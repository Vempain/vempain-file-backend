package fi.poltsi.vempain.file.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Request for changing tags in files")
public class TagOperationRequest {
	@NotBlank
	private String tagName;

	private String replacementTagName;

	private String tagNameDe;
	private String tagNameEn;
	private String tagNameEs;
	private String tagNameFi;
	private String tagNameSv;

	private List<Long> fileIds;
}
