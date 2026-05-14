package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileContentServiceUTC {

	private final FileRepository     fileRepository = mock(FileRepository.class);
	private       FileContentService fileContentService;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		fileContentService = new FileContentService(fileRepository);
		ReflectionTestUtils.setField(fileContentService, "originalRootDirectory", tempDir.toString());
	}

	@Test
	void resolveOriginalFile_returnsResolvedFile() throws Exception {
		var fileDir = tempDir.resolve("audio/test");
		Files.createDirectories(fileDir);
		var target = fileDir.resolve("song.txt");
		Files.writeString(target, "hello world");

		var entity = mock(FileEntity.class);
		when(entity.getFilePath()).thenReturn("/audio/test");
		when(entity.getFilename()).thenReturn("song.txt");
		when(entity.getMimetype()).thenReturn("text/plain");
		when(fileRepository.findById(1L)).thenReturn(Optional.of(entity));

		var result = fileContentService.resolveOriginalFile(1L);

		assertThat(result.absolutePath()).isEqualTo(target);
		assertThat(result.filename()).isEqualTo("song.txt");
		assertThat(result.mimetype()).isEqualTo("text/plain");
		assertThat(result.size()).isEqualTo(11L);
	}

	@Test
	void resolveOriginalFile_missingEntity_throwsNotFound() {
		when(fileRepository.findById(2L)).thenReturn(Optional.empty());

		var ex = assertThrows(ResponseStatusException.class, () -> fileContentService.resolveOriginalFile(2L));
		assertThat(ex.getStatusCode()
		             .value()).isEqualTo(404);
	}

	@Test
	void resolveOriginalFile_pathTraversal_throwsBadRequest() {
		var entity = mock(FileEntity.class);
		when(entity.getFilePath()).thenReturn("/../../etc");
		when(entity.getFilename()).thenReturn("passwd");
		when(fileRepository.findById(3L)).thenReturn(Optional.of(entity));

		var ex = assertThrows(ResponseStatusException.class, () -> fileContentService.resolveOriginalFile(3L));
		assertThat(ex.getStatusCode()
		             .value()).isEqualTo(400);
	}
}

