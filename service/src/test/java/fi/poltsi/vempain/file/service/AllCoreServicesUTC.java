package fi.poltsi.vempain.file.service;

import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.file.api.PathCompletionEnum;
import fi.poltsi.vempain.file.api.request.PathCompletionRequest;
import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.TagEntity;
import fi.poltsi.vempain.file.feign.VempainAdminFileClient;
import fi.poltsi.vempain.file.feign.VempainAdminFileIngestClient;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.FileTagRepository;
import fi.poltsi.vempain.file.repository.GpsLocationRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllCoreServicesUTC {

	@Mock
	private ExportFileRepository                                   exportFileRepository;
	@Mock
	private FileRepository                                         fileRepository;
	@Mock
	private TagRepository                                          tagRepository;
	@Mock
	private FileTagRepository                                      fileTagRepository;
	@Mock
	private FileGroupRepository                                    fileGroupRepository;
	@Mock
	private MetadataRepository                                     metadataRepository;
	@Mock
	private VempainAdminService                                    vempainAdminService;
	@Mock
	private TagService                                             tagService;
	@Mock
	private LocationService                                        locationService;
	@Mock
	private fi.poltsi.vempain.file.feign.VempainAdminTokenProvider tokenProvider;
	@Mock
	private fi.poltsi.vempain.file.tools.ImageTool                 imageTool;
	@Mock
	private org.springframework.context.ApplicationContext         applicationContext;
	@Mock
	private fi.poltsi.vempain.file.repository.FileTagRepository    fileTagRepo;
	@Mock
	private fi.poltsi.vempain.file.repository.FileGroupRepository  fgRepo;
	@Mock
	private fi.poltsi.vempain.file.repository.files.FileRepository fileRepo;
	@Mock
	private fi.poltsi.vempain.file.repository.TagRepository        tagRepo;
	@Mock
	private fi.poltsi.vempain.file.repository.MetadataRepository   metaRepo;
	@Mock
	private fi.poltsi.vempain.file.repository.ExportFileRepository expRepo;
	@Mock
	private fi.poltsi.vempain.auth.service.AclService              aclService;
	@Mock
	private ExportedFilesService                                   exportedFilesService;
	@Mock
	private GpsLocationRepository                                  gpsLocationRepository;
	@Mock
	private VempainAdminFileIngestClient                           ingestClient;
	@Mock
	private VempainAdminFileClient                                 fileClient;
	@Mock
	private ObjectMapper                                           objectMapper;
	@Mock
	private DirectoryProcessorService                              directoryProcessorService;

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

	@Test
	void derivativeLookupServiceUTC_usesDatabaseMatch() {
		var service = new DerivativeLookupService(exportFileRepository, fileRepository);
		ReflectionTestUtils.setField(service, "exportDirectory", "/tmp/export");
		var mockFileEntity = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.FileEntity.class);
		when(mockFileEntity.getFilePath()).thenReturn("/path");
		when(mockFileEntity.getFilename()).thenReturn("name.jpg");
		when(fileRepository.findByOriginalDocumentId("doc-1")).thenReturn(mockFileEntity);

		var result = service.findOriginal("/any", "name.jpg", "doc-1");
		assertThat(result).isNotNull();
	}

	@Test
	void pathCompletionServiceUTC_listsSubDirectories() throws IOException {
		var root  = Files.createTempDirectory("path-completion-root");
		var child = root.resolve("album");
		Files.createDirectories(child);
		var service = new PathCompletionService();
		ReflectionTestUtils.setField(service, "originalRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "exportedRootDirectory", root.toString());

		var request  = new PathCompletionRequest("/", PathCompletionEnum.ORIGINAL);
		var response = service.completePath(request);
		assertThat(response.getCompletions()).contains("/album");
	}

	@Test
	void fileScannerServiceUTC_returnsResponse() {
		var service = new FileScannerService(directoryProcessorService);
		ReflectionTestUtils.setField(service, "originalRootDirectory", "/tmp");
		ReflectionTestUtils.setField(service, "exportRootDirectory", "/tmp");

		var request  = new ScanRequest();
		var response = service.scanDirectories(request);
		assertThat(response).isNotNull();
	}

	@Test
	void publishProgressStoreUTC_tracksCounters() {
		var store = new PublishProgressStore();
		store.init(2);
		store.markScheduled(1L);
		store.markStarted(1L);
		store.markCompleted(1L);
		store.markFailed(2L);

		assertThat(store.getTotal()).isEqualTo(2);
		assertThat(store.getScheduled()).isEqualTo(1);
		assertThat(store.getStarted()).isEqualTo(1);
		assertThat(store.getCompleted()).isEqualTo(1);
		assertThat(store.getFailed()).isEqualTo(1);
		assertThat(store.getPerGroupStatus()).hasSize(2);
	}

	@Test
	void tagServiceUTC_getAllMapsEntities() {
		var service = new TagService(tagRepository, fileTagRepository);
		var tag     = TagEntity.builder()
		                       .id(1L)
		                       .tagName("nature")
		                       .build();
		when(tagRepository.findAll()).thenReturn(List.of(tag));

		var result = service.getAllTags();
		assertThat(result).hasSize(1);
		assertThat(result.getFirst()
		                 .getTagName()).isEqualTo("nature");
	}

	@Test
	void fileGroupServiceUTC_getByIdNotFound() {
		var service = new FileGroupService(fileGroupRepository, fileRepository);
		when(fileGroupRepository.findById(999L)).thenReturn(Optional.empty());
		assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.getById(999L));
	}

	@Test
	void publishServiceUTC_countFilesInGroup() {
		var service = new PublishService(fileGroupRepository, exportFileRepository, metadataRepository, vempainAdminService,
		                                 tagService, locationService, tokenProvider, imageTool, applicationContext, new PublishProgressStore());
		when(fileGroupRepository.countById(10L)).thenReturn(7L);
		assertThat(service.countFilesInGroup(10L)).isEqualTo(7L);
	}

	@Test
	void vempainAdminServiceUTC_throwsOnNon2xx() {
		var service = new VempainAdminService(ingestClient, fileClient, objectMapper);
		var request = FileIngestRequest.builder()
		                               .fileName("a.jpg")
		                               .mimeType("image/jpeg")
		                               .build();
		when(objectMapper.writeValueAsString(any(FileIngestRequest.class))).thenReturn("{}");
		var feignRequest = Request.create(Request.HttpMethod.POST, "/ingest", Map.of(), Request.Body.empty(), new RequestTemplate());
		var response     = Response.builder()
		                           .request(feignRequest)
		                           .status(500)
		                           .reason("error")
		                           .headers(Map.of())
		                           .build();
		when(ingestClient.ingest(any(), any())).thenReturn(ResponseEntity.status(500)
		                                                                 .build());

		assertThrows(VempainAuthenticationException.class, () -> service.uploadAsSiteFile(Path.of("/tmp/no-file.jpg")
		                                                                                      .toFile(), request));
	}

	@Test
	void directoryProcessorServiceUTC_emptyDirectoryReturnsZeroCounts() throws IOException {
		var service = new DirectoryProcessorService(fgRepo, fileRepo, tagRepo, metaRepo, fileTagRepo, expRepo, aclService, exportedFilesService, gpsLocationRepository);
		var dir     = Files.createTempDirectory("empty-original");
		ReflectionTestUtils.setField(service, "originalRootDirectory", dir.getParent()
		                                                                  .toString());
		var result = service.processOriginalDirectory(dir, new StringBuilder(), new java.util.ArrayList<>(), new java.util.ArrayList<>());
		assertThat(result.getFirst()).isEqualTo(0L);
		assertThat(result.get(1)).isEqualTo(0L);
	}
}

