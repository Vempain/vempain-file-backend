package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FileGroupService {

	private final FileGroupRepository fileGroupRepository;

	@Transactional(readOnly = true)
	public List<FileGroupResponse> getAll() {
		return fileGroupRepository.findAll()
								  .stream()
								  .map(FileGroupEntity::toResponse)
								  .toList();
	}

	@Transactional(readOnly = true)
	public FileGroupResponse getById(Long id) {
		FileGroupEntity entity = fileGroupRepository.findById(id)
													.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File group not found"));
		return entity.toResponse();
	}
}

