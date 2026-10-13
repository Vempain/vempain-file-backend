package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.file.api.request.FileGroupRequest;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileGroupServiceUTC {

	@Mock
	private FileGroupRepository  fileGroupRepository;
	@Mock
	private FileRepository       fileRepository;
	@Mock
	private FileResponseEnricher fileResponseEnricher;
	@Mock
	private FileAclService fileAclService;

	@Test
	void getByIdNotFound() {
		var service = new FileGroupService(fileGroupRepository, fileRepository, fileResponseEnricher, fileAclService);
		when(fileGroupRepository.findById(999L)).thenReturn(Optional.empty());

		assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.getById(999L));
	}


	@Test
	void getAllMapsEverySortColumnThroughTheAllowList() {
		var service  = new FileGroupService(fileGroupRepository, fileRepository, fileResponseEnricher, fileAclService);
		var pageable = ArgumentCaptor.forClass(Pageable.class);
		when(fileGroupRepository.searchFileGroups(any(), anyBoolean(), pageable.capture())).thenReturn(new PageImpl<>(List.of()));

		for (var sortBy : List.of("id", "group_name", "description", "file_count", "bogus")) {
			service.getAll(new PagedRequest(0, 10, sortBy, Sort.Direction.DESC, null, null));
		}
		service.getAll(new PagedRequest(0, 10, null, null, null, null));

		assertThat(pageable.getAllValues()
						   .stream()
						   .map(page -> page.getSort()
											.iterator()
											.next()
											.getProperty())
						   .toList()).containsExactly("id", "groupName", "description", "file_count", "path", "path");
	}

	@Test
	void updateRefusesToRemoveAFileFromItsLastGroupAndOtherwiseReplacesTheFiles() {
		var service = new FileGroupService(fileGroupRepository, fileRepository, fileResponseEnricher, fileAclService);
		var group = FileGroupEntity.builder()
								   .id(1L)
								   .path("/p")
								   .groupName("g")
								   .description("")
								   .build();
		var onlyHere = new ImageFileEntity();
		onlyHere.setId(5L);
		onlyHere.setFileGroups(new HashSet<>(List.of(group)));
		group.setFiles(new ArrayList<>(List.of(onlyHere)));
		when(fileGroupRepository.findById(1L)).thenReturn(java.util.Optional.of(group));
		when(fileRepository.findAllById(List.of())).thenReturn(List.of());
		var request = FileGroupRequest.builder()
									  .id(1L)
									  .path("/p")
									  .groupName("renamed")
									  .fileIds(List.of())
									  .build();

		var refused = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.updateFileGroup(request));
		assertThat(refused.getStatusCode()
						  .value()).isEqualTo(400);

		var elsewhere = FileGroupEntity.builder()
									   .id(2L)
									   .build();
		onlyHere.getFileGroups()
				.add(elsewhere);
		when(fileGroupRepository.save(group)).thenReturn(group);
		var response = service.updateFileGroup(request);

		assertThat(response.getGroupName()).isEqualTo("renamed");
		assertThat(response.getFiles()).isEmpty();
		assertThat(group.getFiles()).isEmpty();
		assertThat(onlyHere.getFileGroups()).containsExactly(elsewhere);
	}
}
