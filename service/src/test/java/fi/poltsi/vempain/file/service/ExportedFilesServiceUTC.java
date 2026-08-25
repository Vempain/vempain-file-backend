package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportedFilesServiceUTC {

	@Mock
	private ExportFileRepository exportFileRepository;

	@Test
	void exportedFilesServiceUTC() {
		var service = new ExportedFilesService(exportFileRepository);
		when(exportFileRepository.findByFilePathAndFilename("/x", "a.jpg")).thenReturn(Optional.empty());
		assertThat(service.existsByPathAndFilename("/x", "a.jpg")).isFalse();

		when(exportFileRepository.findByOriginalDocumentId("doc-1")).thenReturn(new ExportFileEntity());
		assertThat(service.existsByOriginalDocumentId("doc-1")).isTrue();

		var entity = new ExportFileEntity();
		when(exportFileRepository.save(entity)).thenReturn(entity);
		assertThat(service.save(entity)).isSameAs(entity);
	}
}
