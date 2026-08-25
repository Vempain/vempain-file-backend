package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileGroupServiceUTC {

	@Mock
	private FileGroupRepository  fileGroupRepository;
	@Mock
	private FileRepository       fileRepository;
	@Mock
	private FileResponseEnricher fileResponseEnricher;

	@Test
	void getByIdNotFound() {
		var service = new FileGroupService(fileGroupRepository, fileRepository, fileResponseEnricher);
		when(fileGroupRepository.findById(999L)).thenReturn(Optional.empty());

		assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.getById(999L));
	}
}
