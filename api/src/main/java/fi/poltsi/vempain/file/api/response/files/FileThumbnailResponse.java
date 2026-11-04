package fi.poltsi.vempain.file.api.response.files;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Response DTO representing a file thumbnail sub-resource")
public class FileThumbnailResponse {

    @Schema(description = "Thumbnail identifier", example = "123")
    private Long id;

    @Schema(description = "Identifier of the parent file", example = "42")
    private Long fileId;

    @Schema(description = "Name of the thumbnail file", example = "image.jpeg")
    private String filename;

    @Schema(description = "Path of the thumbnail relative to the original root", example = "/thumb/photos")
    private String filePath;

    @Schema(description = "MIME type of the thumbnail", example = "image/jpeg")
    private String mimetype;

    @Schema(description = "Size of the thumbnail in bytes", example = "10240")
    private long filesize;

    @Schema(description = "SHA-256 checksum of the thumbnail")
    private String sha256sum;

    @Schema(description = "Width of the thumbnail in pixels", example = "320", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer width;

    @Schema(description = "Height of the thumbnail in pixels", example = "240", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer height;

    @Schema(description = "Creation time of the thumbnail")
    private Instant created;
}
