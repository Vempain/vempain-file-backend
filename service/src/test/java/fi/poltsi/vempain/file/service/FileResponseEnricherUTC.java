package fi.poltsi.vempain.file.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileThumbnailEntity;
import fi.poltsi.vempain.file.repository.FileThumbnailRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileResponseEnricherUTC {

    @Mock
    private FileThumbnailRepository thumbnailRepository;

    @Mock
    private FileEntity fileEntity;

    @Test
    void enrichSetsThumbnailIdWhenThumbnailExistsThroughTargetFile() {
        var response = new FileResponse();
        response.setId(42L);
        when(fileEntity.toResponse()).thenReturn(response);
        var thumbnail = FileThumbnailEntity.builder()
            .id(99L)
            .file(fileEntity)
            .build();
        when(thumbnailRepository.findByFileId(42L)).thenReturn(Optional.of(thumbnail));

        var result = new FileResponseEnricher(thumbnailRepository).toResponse(fileEntity);

        assertThat(result.getThumbnail()
            .getId()).isEqualTo(99L);
        verify(thumbnailRepository).findByFileId(42L);
    }

    @Test
    void enrichPreservesNullWhenThumbnailIsAbsent() {
        var response = new FileResponse();
        response.setId(42L);
        when(fileEntity.toResponse()).thenReturn(response);
        when(thumbnailRepository.findByFileId(42L)).thenReturn(Optional.empty());

        assertThat(new FileResponseEnricher(thumbnailRepository).toResponse(fileEntity)
            .getThumbnail()).isNull();
    }

    @Test
    void enrichAllUsesOneBatchAndDeterministicRepositoryResults() {
        when(fileEntity.getId()).thenReturn(42L);
        var first = new FileResponse();
        first.setId(42L);
        var second = new FileResponse();
        second.setId(43L);
        var firstThumbnail = FileThumbnailEntity.builder()
            .id(99L)
            .file(fileEntity)
            .build();
        var secondFile = new fi.poltsi.vempain.file.entity.ImageFileEntity();
        secondFile.setId(43L);
        var secondThumbnail = FileThumbnailEntity.builder()
            .id(100L)
            .file(secondFile)
            .build();
        when(thumbnailRepository.findByFileIdIn(List.of(42L, 43L))).thenReturn(List.of(firstThumbnail, secondThumbnail));

        var responses = List.of(first, second);
        new FileResponseEnricher(thumbnailRepository).enrichAll(responses);

        assertThat(first.getThumbnail()
            .getId()).isEqualTo(99L);
        assertThat(second.getThumbnail()
            .getId()).isEqualTo(100L);
        verify(thumbnailRepository).findByFileIdIn(List.of(42L, 43L));
    }
}
