package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.repository.files.ThumbFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileResponseEnricherUTC {

	@Mock
	private ThumbFileRepository thumbFileRepository;

	@Mock
	private FileEntity fileEntity;

	@Test
	void enrich_setsThumbnailId_whenThumbnailExistsThroughTargetFile() {
		var response = new FileResponse();
		response.setId(42L);
		when(fileEntity.toResponse()).thenReturn(response);
		when(thumbFileRepository.findThumbnailIdByTargetFileId(42L)).thenReturn(Optional.of(99L));

		var result = new FileResponseEnricher(thumbFileRepository).toResponse(fileEntity);

		assertThat(result.getThumbnailId()).isEqualTo(99L);
		verify(thumbFileRepository).findThumbnailIdByTargetFileId(42L);
	}

	@Test
	void enrich_preservesNull_whenThumbnailIsAbsent() {
		var response = new FileResponse();
		response.setId(42L);
		when(fileEntity.toResponse()).thenReturn(response);
		when(thumbFileRepository.findThumbnailIdByTargetFileId(42L)).thenReturn(Optional.empty());

		assertThat(new FileResponseEnricher(thumbFileRepository).toResponse(fileEntity)
		                                                        .getThumbnailId()).isNull();
	}

	@Test
	void enrichAll_usesOneBatchAndDeterministicRepositoryResults() {
		var first = new FileResponse();
		first.setId(42L);
		var second = new FileResponse();
		second.setId(43L);
		when(thumbFileRepository.findThumbnailIdsByTargetFileIds(List.of(42L, 43L)))
				.thenReturn(List.of(new Object[]{42L, 99L}, new Object[]{43L, 100L}));

		var responses = List.of(first, second);
		new FileResponseEnricher(thumbFileRepository).enrichAll(responses);

		assertThat(first.getThumbnailId()).isEqualTo(99L);
		assertThat(second.getThumbnailId()).isEqualTo(100L);
		verify(thumbFileRepository).findThumbnailIdsByTargetFileIds(List.of(42L, 43L));
	}
}
