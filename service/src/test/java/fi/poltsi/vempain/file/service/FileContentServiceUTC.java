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
	private final FileAclService fileAclService = mock(FileAclService.class);
	@TempDir
	Path tempDir;
	private FileContentService fileContentService;

	@BeforeEach
	void setUp() {
		fileContentService = new FileContentService(fileRepository, fileAclService);
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

	@Test
	void resolveOriginalFile_rejectsSymlinkOutsideRoot() throws Exception {
		var link = tempDir.resolve("linked");
		try {
			Files.createSymbolicLink(link, Path.of("/etc"));
		} catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
			return;
		}

		var entity = mock(FileEntity.class);
		when(entity.getFilePath()).thenReturn("/linked");
		when(entity.getFilename()).thenReturn("passwd");
		when(fileRepository.findById(4L)).thenReturn(Optional.of(entity));

		var ex = assertThrows(ResponseStatusException.class, () -> fileContentService.resolveOriginalFile(4L));
		assertThat(ex.getStatusCode()
		             .value()).isEqualTo(400);
	}


	@Test
	void resolveOriginalFile_unavailableRoot_isAnInternalError() {
		ReflectionTestUtils.setField(fileContentService, "originalRootDirectory", "/nonexistent-root-" + java.util.UUID.randomUUID());
		var entity = mock(FileEntity.class);
		when(fileRepository.findById(5L)).thenReturn(Optional.of(entity));

		var ex = assertThrows(ResponseStatusException.class, () -> fileContentService.resolveOriginalFile(5L));
		assertThat(ex.getStatusCode()
					 .value()).isEqualTo(500);
	}

	@Test
	void resolveOriginalFile_missingContent_isNotFound() {
		var entity = mock(FileEntity.class);
		when(entity.getFilePath()).thenReturn("/");
		when(entity.getFilename()).thenReturn("nope.txt");
		when(fileRepository.findById(6L)).thenReturn(Optional.of(entity));

		var ex = assertThrows(ResponseStatusException.class, () -> fileContentService.resolveOriginalFile(6L));
		assertThat(ex.getStatusCode()
					 .value()).isEqualTo(404);
	}

	@Test
	void resolveOriginalFile_normalisesRootAndBackslashDirectories() throws Exception {
		Files.writeString(tempDir.resolve("root.txt"), "root");
		var nested = tempDir.resolve("sub/dir");
		Files.createDirectories(nested);
		Files.writeString(nested.resolve("nested.txt"), "nested");

		var nullPath = mock(FileEntity.class);
		when(nullPath.getFilePath()).thenReturn(null);
		when(nullPath.getFilename()).thenReturn("root.txt");
		when(fileRepository.findById(7L)).thenReturn(Optional.of(nullPath));
		assertThat(fileContentService.resolveOriginalFile(7L)
									 .absolutePath()).isEqualTo(tempDir.resolve("root.txt"));

		var slash = mock(FileEntity.class);
		when(slash.getFilePath()).thenReturn(" / ");
		when(slash.getFilename()).thenReturn("root.txt");
		when(fileRepository.findById(8L)).thenReturn(Optional.of(slash));
		assertThat(fileContentService.resolveOriginalFile(8L)
									 .size()).isEqualTo(4L);

		var backslashes = mock(FileEntity.class);
		when(backslashes.getFilePath()).thenReturn("\\\\sub\\dir");
		when(backslashes.getFilename()).thenReturn("nested.txt");
		when(fileRepository.findById(9L)).thenReturn(Optional.of(backslashes));
		assertThat(fileContentService.resolveOriginalFile(9L)
									 .absolutePath()).isEqualTo(nested.resolve("nested.txt"));
	}


	@Test
	void resolveOriginalFile_fileVanishingBeforeTheSizeLookup_isAnInternalError() throws Exception {
		var target = tempDir.resolve("vanishing.txt");
		Files.writeString(target, "soon gone");
		var entity = mock(FileEntity.class);
		when(entity.getFilePath()).thenReturn("/");
		when(entity.getFilename()).thenReturn("vanishing.txt");
		// the MIME type is read right before the size, so the file is removed in between
		when(entity.getMimetype()).thenAnswer(invocation -> {
			Files.delete(target);
			return "text/plain";
		});
		when(fileRepository.findById(10L)).thenReturn(Optional.of(entity));

		var ex = assertThrows(ResponseStatusException.class, () -> fileContentService.resolveOriginalFile(10L));
		assertThat(ex.getStatusCode()
					 .value()).isEqualTo(500);
	}
}
