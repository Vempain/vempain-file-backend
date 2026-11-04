package fi.poltsi.vempain.file.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import fi.poltsi.vempain.file.api.response.files.FileThumbnailResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "file_thumbnails")
public class FileThumbnailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false, unique = true)
    private FileEntity file;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "mimetype", nullable = false)
    private String mimetype;

    @Column(name = "filesize", nullable = false)
    private long filesize;

    @Column(name = "sha256sum", nullable = false, length = 64)
    private String sha256sum;

    private Integer width;
    private Integer height;

    @Column(name = "created", nullable = false)
    private Instant created;

    public FileThumbnailResponse toResponse() {
        return FileThumbnailResponse.builder()
            .id(id)
            .fileId(file == null ? null : file.getId())
            .filename(filename)
            .filePath(filePath)
            .mimetype(mimetype)
            .filesize(filesize)
            .sha256sum(sha256sum)
            .width(width)
            .height(height)
            .created(created)
            .build();
    }
}
