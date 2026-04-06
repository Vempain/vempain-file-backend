package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.file.repository.DocumentFileRepository;
import fi.poltsi.vempain.file.repository.files.ArchiveFileRepository;
import fi.poltsi.vempain.file.repository.files.AudioFileRepository;
import fi.poltsi.vempain.file.repository.files.BinaryFileRepository;
import fi.poltsi.vempain.file.repository.files.DataFileRepository;
import fi.poltsi.vempain.file.repository.files.ExecutableFileRepository;
import fi.poltsi.vempain.file.repository.files.FontFileRepository;
import fi.poltsi.vempain.file.repository.files.IconFileRepository;
import fi.poltsi.vempain.file.repository.files.ImageFileRepository;
import fi.poltsi.vempain.file.repository.files.InteractiveFileRepository;
import fi.poltsi.vempain.file.repository.files.ThumbFileRepository;
import fi.poltsi.vempain.file.repository.files.VectorFileRepository;
import fi.poltsi.vempain.file.repository.files.VideoFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileTypeServicesUTC {

	@Mock
	private ArchiveFileRepository     archiveFileRepository;
	@Mock
	private AudioFileRepository       audioFileRepository;
	@Mock
	private BinaryFileRepository      binaryFileRepository;
	@Mock
	private DataFileRepository        dataFileRepository;
	@Mock
	private DocumentFileRepository    documentFileRepository;
	@Mock
	private ExecutableFileRepository  executableFileRepository;
	@Mock
	private FontFileRepository        fontFileRepository;
	@Mock
	private IconFileRepository        iconFileRepository;
	@Mock
	private ImageFileRepository       imageFileRepository;
	@Mock
	private InteractiveFileRepository interactiveFileRepository;
	@Mock
	private ThumbFileRepository       thumbFileRepository;
	@Mock
	private VectorFileRepository      vectorFileRepository;
	@Mock
	private VideoFileRepository       videoFileRepository;

	@Test
	void findById_returnsNull_whenMissing_forAllFileTypeServices() {
		when(archiveFileRepository.findById(1L)).thenReturn(Optional.empty());
		when(audioFileRepository.findById(1L)).thenReturn(Optional.empty());
		when(binaryFileRepository.findById(1L)).thenReturn(Optional.empty());
		when(dataFileRepository.findById(1L)).thenReturn(Optional.empty());
		when(documentFileRepository.findById(1L)).thenReturn(Optional.empty());
		when(executableFileRepository.findById(1L)).thenReturn(Optional.empty());
		when(fontFileRepository.findById(1L)).thenReturn(Optional.empty());
		when(iconFileRepository.findById(1L)).thenReturn(Optional.empty());
		when(imageFileRepository.findById(1L)).thenReturn(Optional.empty());
		when(interactiveFileRepository.findById(1L)).thenReturn(Optional.empty());
		when(thumbFileRepository.findById(1L)).thenReturn(Optional.empty());
		when(vectorFileRepository.findById(1L)).thenReturn(Optional.empty());
		when(videoFileRepository.findById(1L)).thenReturn(Optional.empty());

		assertThat(new ArchiveFileService(archiveFileRepository).findById(1L)).isNull();
		assertThat(new AudioFileService(audioFileRepository).findById(1L)).isNull();
		assertThat(new BinaryFileService(binaryFileRepository).findById(1L)).isNull();
		assertThat(new DataFileService(dataFileRepository).findById(1L)).isNull();
		assertThat(new DocumentFileService(documentFileRepository).findById(1L)).isNull();
		assertThat(new ExecutableFileService(executableFileRepository).findById(1L)).isNull();
		assertThat(new FontFileService(fontFileRepository).findById(1L)).isNull();
		assertThat(new IconFileService(iconFileRepository).findById(1L)).isNull();
		assertThat(new ImageFileService(imageFileRepository).findById(1L)).isNull();
		assertThat(new InteractiveFileService(interactiveFileRepository).findById(1L)).isNull();
		assertThat(new ThumbFileService(thumbFileRepository).findById(1L)).isNull();
		assertThat(new VectorFileService(vectorFileRepository).findById(1L)).isNull();
		assertThat(new VideoFileService(videoFileRepository).findById(1L)).isNull();
	}

	@Test
	@SuppressWarnings("unchecked")
	void findAll_returnsPagedResponse_forAllFileTypeServices() {
		var pagedRequest = new PagedRequest();
		pagedRequest.setPage(0);
		pagedRequest.setSize(10);
		pagedRequest.setSearch("file");

		Page<?> emptyPage = Page.empty();
		doReturn(emptyPage).when(archiveFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
		doReturn(emptyPage).when(audioFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
		doReturn(emptyPage).when(binaryFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
		doReturn(emptyPage).when(dataFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
		doReturn(emptyPage).when(documentFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
		doReturn(emptyPage).when(executableFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
		doReturn(emptyPage).when(fontFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
		doReturn(emptyPage).when(iconFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
		doReturn(emptyPage).when(imageFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
		doReturn(emptyPage).when(interactiveFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
		doReturn(emptyPage).when(thumbFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
		doReturn(emptyPage).when(vectorFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
		doReturn(emptyPage).when(videoFileRepository)
		                   .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));

		assertThat(new ArchiveFileService(archiveFileRepository).findAll(pagedRequest)).isNotNull();
		assertThat(new AudioFileService(audioFileRepository).findAll(pagedRequest)).isNotNull();
		assertThat(new BinaryFileService(binaryFileRepository).findAll(pagedRequest)).isNotNull();
		assertThat(new DataFileService(dataFileRepository).findAll(pagedRequest)).isNotNull();
		assertThat(new DocumentFileService(documentFileRepository).findAll(pagedRequest)).isNotNull();
		assertThat(new ExecutableFileService(executableFileRepository).findAll(pagedRequest)).isNotNull();
		assertThat(new FontFileService(fontFileRepository).findAll(pagedRequest)).isNotNull();
		assertThat(new IconFileService(iconFileRepository).findAll(pagedRequest)).isNotNull();
		assertThat(new ImageFileService(imageFileRepository).findAll(pagedRequest)).isNotNull();
		assertThat(new InteractiveFileService(interactiveFileRepository).findAll(pagedRequest)).isNotNull();
		assertThat(new ThumbFileService(thumbFileRepository).findAll(pagedRequest)).isNotNull();
		assertThat(new VectorFileService(vectorFileRepository).findAll(pagedRequest)).isNotNull();
		assertThat(new VideoFileService(videoFileRepository).findAll(pagedRequest)).isNotNull();
	}

	@Test
	void findById_returnsResponse_whenEntityExists_forAllFileTypeServices() {
		var archive     = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.ArchiveFileEntity.class);
		var audio       = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.AudioFileEntity.class);
		var binary      = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.BinaryFileEntity.class);
		var data        = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.DataFileEntity.class);
		var document    = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.DocumentFileEntity.class);
		var executable  = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.ExecutableFileEntity.class);
		var font        = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.FontFileEntity.class);
		var icon        = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.IconFileEntity.class);
		var image       = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.ImageFileEntity.class);
		var interactive = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.InteractiveFileEntity.class);
		var thumb       = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.ThumbFileEntity.class);
		var vector      = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.VectorFileEntity.class);
		var video       = org.mockito.Mockito.mock(fi.poltsi.vempain.file.entity.VideoFileEntity.class);

		when(archive.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.ArchiveFileResponse());
		when(audio.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.AudioFileResponse());
		when(binary.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.BinaryFileResponse());
		when(data.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.DataFileResponse());
		when(document.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.DocumentFileResponse());
		when(executable.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.ExecutableFileResponse());
		when(font.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.FontFileResponse());
		when(icon.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.IconFileResponse());
		when(image.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.ImageFileResponse());
		when(interactive.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.InteractiveFileResponse());
		when(thumb.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.ThumbFileResponse());
		when(vector.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.VectorFileResponse());
		when(video.toResponse()).thenReturn(new fi.poltsi.vempain.file.api.response.files.VideoFileResponse());

		when(archiveFileRepository.findById(2L)).thenReturn(Optional.of(archive));
		when(audioFileRepository.findById(2L)).thenReturn(Optional.of(audio));
		when(binaryFileRepository.findById(2L)).thenReturn(Optional.of(binary));
		when(dataFileRepository.findById(2L)).thenReturn(Optional.of(data));
		when(documentFileRepository.findById(2L)).thenReturn(Optional.of(document));
		when(executableFileRepository.findById(2L)).thenReturn(Optional.of(executable));
		when(fontFileRepository.findById(2L)).thenReturn(Optional.of(font));
		when(iconFileRepository.findById(2L)).thenReturn(Optional.of(icon));
		when(imageFileRepository.findById(2L)).thenReturn(Optional.of(image));
		when(interactiveFileRepository.findById(2L)).thenReturn(Optional.of(interactive));
		when(thumbFileRepository.findById(2L)).thenReturn(Optional.of(thumb));
		when(vectorFileRepository.findById(2L)).thenReturn(Optional.of(vector));
		when(videoFileRepository.findById(2L)).thenReturn(Optional.of(video));

		assertThat(new ArchiveFileService(archiveFileRepository).findById(2L)).isNotNull();
		assertThat(new AudioFileService(audioFileRepository).findById(2L)).isNotNull();
		assertThat(new BinaryFileService(binaryFileRepository).findById(2L)).isNotNull();
		assertThat(new DataFileService(dataFileRepository).findById(2L)).isNotNull();
		assertThat(new DocumentFileService(documentFileRepository).findById(2L)).isNotNull();
		assertThat(new ExecutableFileService(executableFileRepository).findById(2L)).isNotNull();
		assertThat(new FontFileService(fontFileRepository).findById(2L)).isNotNull();
		assertThat(new IconFileService(iconFileRepository).findById(2L)).isNotNull();
		assertThat(new ImageFileService(imageFileRepository).findById(2L)).isNotNull();
		assertThat(new InteractiveFileService(interactiveFileRepository).findById(2L)).isNotNull();
		assertThat(new ThumbFileService(thumbFileRepository).findById(2L)).isNotNull();
		assertThat(new VectorFileService(vectorFileRepository).findById(2L)).isNotNull();
		assertThat(new VideoFileService(videoFileRepository).findById(2L)).isNotNull();
	}

	@Test
	void delete_returnsExpectedStatus_forAllFileTypeServices() {
		var archiveService     = new ArchiveFileService(archiveFileRepository);
		var audioService       = new AudioFileService(audioFileRepository);
		var binaryService      = new BinaryFileService(binaryFileRepository);
		var dataService        = new DataFileService(dataFileRepository);
		var documentService    = new DocumentFileService(documentFileRepository);
		var executableService  = new ExecutableFileService(executableFileRepository);
		var fontService        = new FontFileService(fontFileRepository);
		var iconService        = new IconFileService(iconFileRepository);
		var imageService       = new ImageFileService(imageFileRepository);
		var interactiveService = new InteractiveFileService(interactiveFileRepository);
		var thumbService       = new ThumbFileService(thumbFileRepository);
		var vectorService      = new VectorFileService(vectorFileRepository);
		var videoService       = new VideoFileService(videoFileRepository);

		when(archiveFileRepository.existsById(1L)).thenReturn(true);
		when(audioFileRepository.existsById(1L)).thenReturn(true);
		when(binaryFileRepository.existsById(1L)).thenReturn(true);
		when(dataFileRepository.existsById(1L)).thenReturn(true);
		when(documentFileRepository.existsById(1L)).thenReturn(true);
		when(executableFileRepository.existsById(1L)).thenReturn(true);
		when(fontFileRepository.existsById(1L)).thenReturn(true);
		when(iconFileRepository.existsById(1L)).thenReturn(true);
		when(imageFileRepository.existsById(1L)).thenReturn(true);
		when(interactiveFileRepository.existsById(1L)).thenReturn(true);
		when(thumbFileRepository.existsById(1L)).thenReturn(true);
		when(vectorFileRepository.existsById(1L)).thenReturn(true);
		when(videoFileRepository.existsById(1L)).thenReturn(true);

		assertThat(archiveService.delete(1L)).isEqualTo(HttpStatus.OK);
		assertThat(audioService.delete(1L)).isEqualTo(HttpStatus.OK);
		assertThat(binaryService.delete(1L)).isEqualTo(HttpStatus.OK);
		assertThat(dataService.delete(1L)).isEqualTo(HttpStatus.OK);
		assertThat(documentService.delete(1L)).isEqualTo(HttpStatus.OK);
		assertThat(executableService.delete(1L)).isEqualTo(HttpStatus.OK);
		assertThat(fontService.delete(1L)).isEqualTo(HttpStatus.OK);
		assertThat(iconService.delete(1L)).isEqualTo(HttpStatus.OK);
		assertThat(imageService.delete(1L)).isEqualTo(HttpStatus.OK);
		assertThat(interactiveService.delete(1L)).isEqualTo(HttpStatus.OK);
		assertThat(thumbService.delete(1L)).isEqualTo(HttpStatus.OK);
		assertThat(vectorService.delete(1L)).isEqualTo(HttpStatus.OK);
		assertThat(videoService.delete(1L)).isEqualTo(HttpStatus.OK);

		when(archiveFileRepository.existsById(9L)).thenReturn(false);
		when(audioFileRepository.existsById(9L)).thenReturn(false);
		when(binaryFileRepository.existsById(9L)).thenReturn(false);
		when(dataFileRepository.existsById(9L)).thenReturn(false);
		when(documentFileRepository.existsById(9L)).thenReturn(false);
		when(executableFileRepository.existsById(9L)).thenReturn(false);
		when(fontFileRepository.existsById(9L)).thenReturn(false);
		when(iconFileRepository.existsById(9L)).thenReturn(false);
		when(imageFileRepository.existsById(9L)).thenReturn(false);
		when(interactiveFileRepository.existsById(9L)).thenReturn(false);
		when(thumbFileRepository.existsById(9L)).thenReturn(false);
		when(vectorFileRepository.existsById(9L)).thenReturn(false);
		when(videoFileRepository.existsById(9L)).thenReturn(false);

		assertThat(archiveService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(audioService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(binaryService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(dataService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(documentService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(executableService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(fontService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(iconService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(imageService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(interactiveService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(thumbService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(vectorService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(videoService.delete(9L)).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
