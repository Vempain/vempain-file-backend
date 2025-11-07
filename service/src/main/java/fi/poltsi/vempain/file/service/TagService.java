package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.request.TagRequest;
import fi.poltsi.vempain.file.api.response.TagResponse;
import fi.poltsi.vempain.file.entity.FileTag;
import fi.poltsi.vempain.file.entity.TagEntity;
import fi.poltsi.vempain.file.repository.FileTagRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

	private final TagRepository     tagRepository;
	private final FileTagRepository fileTagRepository;

	public List<TagResponse> getAllTags() {
		return tagRepository.findAll()
							.stream()
							.map(this::mapToResponseDTO)
							.collect(Collectors.toList());
	}

	public TagResponse getTagById(Long id) {
		return tagRepository.findById(id)
							.map(this::mapToResponseDTO)
							.orElseThrow(() -> new IllegalArgumentException("Tag not found"));
	}

	public TagResponse createTag(TagRequest requestDTO) {
		TagEntity tag = mapToEntity(requestDTO);
		tag = tagRepository.save(tag);
		return mapToResponseDTO(tag);
	}

	public TagResponse updateTag(TagRequest requestDTO) {
		Long id = requestDTO.getId();
		if (id == null) {
			throw new IllegalArgumentException("Tag ID must be provided for update");
		}

		TagEntity tag = tagRepository.findById(id)
									 .orElseThrow(() -> new IllegalArgumentException("Tag not found"));
		tag.setTagName(requestDTO.getTagName());
		tag.setTagNameDe(requestDTO.getTagNameDe());
		tag.setTagNameEn(requestDTO.getTagNameEn());
		tag.setTagNameEs(requestDTO.getTagNameEs());
		tag.setTagNameFi(requestDTO.getTagNameFi());
		tag.setTagNameSv(requestDTO.getTagNameSv());
		tag = tagRepository.save(tag);
		return mapToResponseDTO(tag);
	}

	public void deleteTag(Long id) {
		tagRepository.deleteById(id);
	}

	public List<TagRequest> getTagRequestsByFileId(long fileId) {
		var associations = fileTagRepository.findByFileId(fileId);
		return associations.stream()
						   .map(FileTag::getTag)
						   .filter(Objects::nonNull)
						   .map(TagEntity::toRequest)
						   .collect(Collectors.toList());
	}

	private TagEntity mapToEntity(TagRequest dto) {
		return TagEntity.builder()
						.tagName(dto.getTagName())
						.tagNameDe(dto.getTagNameDe())
						.tagNameEn(dto.getTagNameEn())
						.tagNameEs(dto.getTagNameEs())
						.tagNameFi(dto.getTagNameFi())
						.tagNameSv(dto.getTagNameSv())
						.build();
	}

	private TagResponse mapToResponseDTO(TagEntity entity) {
		return new TagResponse(
				entity.getId(),
				entity.getTagName(),
				entity.getTagNameDe(),
				entity.getTagNameEn(),
				entity.getTagNameEs(),
				entity.getTagNameFi(),
				entity.getTagNameSv()
		);
	}
}
