package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.FileTagRepository;
import fi.poltsi.vempain.file.repository.GpsLocationRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectoryProcessorServiceUTC {

	@Mock
	private FileGroupRepository                       fileGroupRepository;
	@Mock
	private FileRepository                            fileRepository;
	@Mock
	private TagRepository                             tagRepository;
	@Mock
	private MetadataRepository                        metadataRepository;
	@Mock
	private FileTagRepository                         fileTagRepository;
	@Mock
	private ExportFileRepository                      exportFileRepository;
	@Mock
	private fi.poltsi.vempain.auth.service.AclService aclService;
	@Mock
	private ExportedFilesService                      exportedFilesService;
	@Mock
	private GpsLocationRepository                     gpsLocationRepository;

	private Path testRoot;

	@AfterEach
	void removeTestDirectory() throws IOException {
		if (testRoot != null && Files.exists(testRoot)) {
			try (var paths = Files.walk(testRoot)) {
				paths.sorted(java.util.Comparator.reverseOrder())
					 .forEach(path -> path.toFile()
				                          .delete());
			}
		}
	}

	@Test
	void processOriginalDirectoryCountsSuccessfulFailedSkippedAndIoErrors() throws Exception {
		testRoot = Path.of("build", "dps-utc-" + UUID.randomUUID());
		var leaf = Files.createDirectories(testRoot.resolve("album"));
		for (var name : List.of("success.jpg", "failed.jpg", "skipped.jpg", "broken.jpg")) {
			Files.writeString(leaf.resolve(name), name);
		}
		var group = FileGroupEntity.builder()
		                           .path("/album")
		                           .groupName("album")
		                           .build();
		when(fileGroupRepository.findByPathAndGroupName("/", "album")).thenReturn(Optional.empty());
		when(fileGroupRepository.save(any(FileGroupEntity.class))).thenReturn(group);

		var stored = new ImageFileEntity();
		stored.setFilename("success.jpg");
		when(fileRepository.findByFilePathAndFilename("/", "success.jpg")).thenReturn(Optional.of(stored));

		var service = spy(newService());
		ReflectionTestUtils.setField(service, "originalRootDirectory", testRoot.toString());
		doAnswer(invocation -> {
			var file = invocation.getArgument(0, java.io.File.class);
			return switch (file.getName()) {
				case "success.jpg" -> Boolean.TRUE;
				case "failed.jpg" -> Boolean.FALSE;
				case "skipped.jpg" -> null;
				default -> throw new IOException("broken");
			};
		}).when(service)
		  .processOriginalFile(any(java.io.File.class), any(FileGroupEntity.class));

		var errors    = new StringBuilder();
		var failed    = new ArrayList<String>();
		var responses = new ArrayList<fi.poltsi.vempain.file.api.response.files.FileResponse>();
		var result    = service.processOriginalDirectory(leaf, errors, failed, responses);

		assertThat(result).containsExactly(4L, 1L);
		assertThat(failed).containsExactly("failed.jpg");
		assertThat(responses).hasSize(1);
		assertThat(errors).contains("broken.jpg");
		verify(fileGroupRepository).save(any(FileGroupEntity.class));
	}

	@Test
	void processOriginalDirectoryUsesExistingGroupAndDoesNotRequireResponseWhenFileIsSkipped() throws Exception {
		testRoot = Path.of("build", "dps-utc-" + UUID.randomUUID());
		var leaf = Files.createDirectories(testRoot.resolve("album"));
		Files.writeString(leaf.resolve("existing.jpg"), "existing");
		var group = FileGroupEntity.builder()
		                           .path("/album")
		                           .groupName("album")
		                           .build();
		when(fileGroupRepository.findByPathAndGroupName("/", "album")).thenReturn(Optional.of(group));

		var service = spy(newService());
		ReflectionTestUtils.setField(service, "originalRootDirectory", testRoot.toString());
		doAnswer(invocation -> null).when(service)
									.processOriginalFile(any(java.io.File.class), any(FileGroupEntity.class));

		var result = service.processOriginalDirectory(leaf, new StringBuilder(), new ArrayList<>(), new ArrayList<>());

		assertThat(result).containsExactly(1L, 0L);
		verify(fileGroupRepository, org.mockito.Mockito.never()).save(any(FileGroupEntity.class));
	}

	@Test
	void processOriginalDirectory_emptyDirectoryReturnsZeroCounts() throws IOException {
		testRoot = Path.of("build", "dps-empty-utc-" + UUID.randomUUID());
		var emptyDirectory = Files.createDirectories(testRoot.resolve("empty"));
		var service        = newService();
		ReflectionTestUtils.setField(service, "originalRootDirectory", testRoot.toString());

		var result = service.processOriginalDirectory(emptyDirectory, new StringBuilder(), new ArrayList<>(), new ArrayList<>());

		assertThat(result).containsExactly(0L, 0L);
	}

	@Test
	void processExportDirectorySkipsAlreadyStoredMatchingFile() throws Exception {
		testRoot = Path.of("build", "dps-utc-" + UUID.randomUUID());
		var leaf     = Files.createDirectories(testRoot.resolve("export"));
		var file     = Files.writeString(leaf.resolve("thumb.jpg"), "thumbnail")
		                    .toFile();
		var existing = mock(ExportFileEntity.class);
		when(existing.getSha256sum()).thenReturn(fi.poltsi.vempain.file.tools.FileTool.computeSha256(file));
		when(exportFileRepository.findByFilePathAndFilename("/export", "thumb.jpg")).thenReturn(Optional.of(existing));

		var service = newService();
		ReflectionTestUtils.setField(service, "exportRootDirectory", testRoot.toString());
		var result = service.processExportDirectory(leaf, new StringBuilder(), new ArrayList<>(), new ArrayList<>());

		assertThat(result).containsExactly(1L, 0L);
		verify(exportFileRepository).findByFilePathAndFilename("/export", "thumb.jpg");
	}

	private DirectoryProcessorService newService() {
		return new DirectoryProcessorService(fileGroupRepository, fileRepository, tagRepository, metadataRepository,
											 fileTagRepository, exportFileRepository, aclService,
											 exportedFilesService, gpsLocationRepository);
	}
}
