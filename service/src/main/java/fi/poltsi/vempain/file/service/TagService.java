package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.file.api.TaskTypeEnum;
import fi.poltsi.vempain.file.api.request.TagOperationRequest;
import fi.poltsi.vempain.file.api.request.TagRequest;
import fi.poltsi.vempain.file.api.response.TagResponse;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileTag;
import fi.poltsi.vempain.file.entity.TagEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileTagRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.repository.files.ThumbFileRepository;
import fi.poltsi.vempain.file.service.files.FileSearchHelper;
import fi.poltsi.vempain.file.task.Compensation;
import fi.poltsi.vempain.file.task.TaskProgress;
import fi.poltsi.vempain.file.task.TaskRunner;
import fi.poltsi.vempain.file.tools.LikePatterns;
import fi.poltsi.vempain.file.tools.MetadataTool;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {
	private final TagRepository              tagRepository;
	private final FileTagRepository fileTagRepository;
	private final FileRepository             fileRepository;
	private final FileResponseEnricher       fileResponseEnricher;
	private final ExportFileRepository       exportFileRepository;
	private final ThumbFileRepository        thumbFileRepository;
	private final DirectoryProcessorService  directoryProcessorService;
	private final ThumbnailGenerationService thumbnailGenerationService;
	private final FileAclService     fileAclService;
	private final TaskRunner         taskRunner;
	private final ApplicationContext applicationContext;

	@Value("${vempain.original-root-directory}")
	private String originalRootDirectory;
	@Value("${vempain.export-root-directory}")
	private String exportRootDirectory;
	@Value("${vempain.generate-missing-thumbnails.thumb-image-size}")
	private int    thumbnailMinimumSize;
	@Value("${vempain.generate-missing-thumbnails.thumb-image-quality}")
	private float  thumbnailQuality;

	public List<TagResponse> getAllTags() {
		return tagRepository.findAll()
		                    .stream()
		                    .map(this::mapToResponseDTO)
		                    .collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public PagedResponse<TagResponse> getAllTagsPageable(PagedRequest pagedRequest) {
		var page = Math.max(0, pagedRequest.getPage());
		var size = Math.clamp(pagedRequest.getSize(), 1, 200);
		var sort = buildTagSort(pagedRequest.getSortBy(), pagedRequest.getDirection());
		var specification = buildTagSpecification(pagedRequest.getSearch(),
		                                          Boolean.TRUE.equals(pagedRequest.getCaseSensitive()));
		var result  = tagRepository.findAll(specification, PageRequest.of(page, size, sort));
		var content = result.getContent()
		                    .stream()
		                    .map(this::mapToResponseDTO)
		                    .toList();
		return PagedResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements(),
		                        result.getTotalPages(), result.isFirst(), result.isLast());
	}

	public TagResponse getTagById(Long id) {
		return tagRepository.findById(id)
		                    .map(this::mapToResponseDTO)
		                    .orElseThrow(() -> new IllegalArgumentException("Tag not found"));
	}

	@Transactional(readOnly = true)
	public PagedResponse<FileResponse> getFilesByTag(Long tagId, PagedRequest pagedRequest) {
		if (!tagRepository.existsById(tagId)) {
			throw new IllegalArgumentException("Tag not found");
		}
		var page = Math.max(0, pagedRequest.getPage());
		var size = Math.clamp(pagedRequest.getSize(), 1, 200);
		var sort = FileSearchHelper.buildSort(pagedRequest.getSortBy(), pagedRequest.getDirection());
		Specification<FileEntity> searchSpec =
				FileSearchHelper.buildSpecification(pagedRequest.getSearch(), Boolean.TRUE.equals(pagedRequest.getCaseSensitive()));
		Specification<FileEntity> tagSpec = (root, query, cb) -> {
			query.distinct(true);
			return cb.equal(root.join("tags")
			                    .get("id"), tagId);
		};
		var result = fileRepository.findAll(fileAclService.readableFiles(searchSpec == null ? tagSpec : tagSpec.and(searchSpec)),
											PageRequest.of(page, size, sort));
		var content = fileResponseEnricher.<FileResponse>toResponses(result.getContent());
		return PagedResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements(),
								result.getTotalPages(), result.isFirst(), result.isLast());
	}

	@Transactional
	public TagResponse createTag(TagRequest requestDTO) {
		tagRepository.lockTagMutations();
		return mapToResponseDTO(tagRepository.save(mapToEntity(requestDTO)));
	}

	@Transactional
	public TagResponse updateTag(TagRequest requestDTO) {
		if (requestDTO.getId() == null) {
			throw new IllegalArgumentException("Tag ID must be provided for update");
		}
		tagRepository.lockTagMutations();
		var tag = tagRepository.findById(requestDTO.getId())
							   .orElseThrow(() -> new IllegalArgumentException("Tag not found"));
		requireModify(tag.getFiles());
		tag.setTagName(requestDTO.getTagName());
		tag.setTagNameDe(requestDTO.getTagNameDe());
		tag.setTagNameEn(requestDTO.getTagNameEn());
		tag.setTagNameEs(requestDTO.getTagNameEs());
		tag.setTagNameFi(requestDTO.getTagNameFi());
		tag.setTagNameSv(requestDTO.getTagNameSv());
		return mapToResponseDTO(tagRepository.save(tag));
	}

	public void deleteTag(Long id) {
		tagRepository.findById(id)
		             .ifPresent(tag -> {
						 requireModify(tag.getFiles());
					 });
		tagRepository.deleteById(id);
	}

	public List<TagRequest> getTagRequestsByFileId(long fileId) {
		return fileTagRepository.findByFileId(fileId)
		                        .stream()
		                        .map(FileTag::getTag)
		                        .filter(Objects::nonNull)
								.map(TagEntity::toRequest)
		                        .collect(Collectors.toList());
	}

	@Transactional
	public void addTag(TagOperationRequest request) {
		requireModify(fileRepository.findAllById(new LinkedHashSet<>(request.getFileIds())));
		var tag = findOrCreateTag(request.getTagName());
		tag.setTagNameDe(request.getTagNameDe());
		tag.setTagNameEn(request.getTagNameEn());
		tag.setTagNameEs(request.getTagNameEs());
		tag.setTagNameFi(request.getTagNameFi());
		tag.setTagNameSv(request.getTagNameSv());
		tagRepository.save(tag);
		mutate(request, request.getFileIds(), Operation.ADD, null);
	}

	@Transactional
	public void removeTag(TagOperationRequest request, boolean all) {
		var files = all ? filesForTag(request.getTagName()) : request.getFileIds();
		requireModify(fileRepository.findAllById(new LinkedHashSet<>(files)));
		mutate(request, files, Operation.REMOVE, null);
		deleteTagIfUnused(request.getTagName());
	}

	/**
	 * Removes the tag from every tagged file as a background task (one step per file). ACL checks run synchronously.
	 */
	public TaskProgress removeTagFromAllAsTask(TagOperationRequest request) {
		var files = filesForTag(request.getTagName());
		requireModify(fileRepository.findAllById(new LinkedHashSet<>(files)));
		return submitTagTask(TaskTypeEnum.TAG_REMOVE_FROM_ALL, "Remove tag '" + request.getTagName() + "' from " + files.size() + " files", request, files,
							 Operation.REMOVE, true, null);
	}

	@Transactional
	public void replaceTag(TagOperationRequest request, boolean all) {
		requireReplacement(request);
		requireExistingTag(request.getReplacementTagName());
		var files = all ? filesForTag(request.getTagName()) : request.getFileIds();
		requireModify(fileRepository.findAllById(new LinkedHashSet<>(files)));
		mutate(request, files, Operation.REPLACE, null);
		deleteTagIfUnused(request.getTagName());
	}

	/**
	 * Replaces the tag on every tagged file as a background task (one step per file). Validation and ACL checks run synchronously.
	 */
	public TaskProgress replaceTagAcrossAllAsTask(TagOperationRequest request) {
		requireReplacement(request);
		requireExistingTag(request.getReplacementTagName());
		var files = filesForTag(request.getTagName());
		requireModify(fileRepository.findAllById(new LinkedHashSet<>(files)));
		return submitTagTask(TaskTypeEnum.TAG_REPLACE_ACROSS_ALL,
							 "Replace tag '" + request.getTagName() + "' with '" + request.getReplacementTagName() + "' on " + files.size() + " files",
							 request, files, Operation.REPLACE, true, null);
	}

	@Transactional
	public void renameTag(TagOperationRequest request, boolean all) {
		var files = prepareRename(request, all);
		mutate(request, files, Operation.REPLACE, null);
	}

	private List<Long> prepareRename(TagOperationRequest request, boolean all) {
		requireReplacement(request);
		tagRepository.lockTagMutations();
		var tag = tagRepository.findByTagName(request.getTagName())
							   .orElseThrow(() -> new IllegalArgumentException("Tag not found"));
		if (!request.getTagName()
		            .equals(request.getReplacementTagName())
		    && tagRepository.findByTagName(request.getReplacementTagName())
		                    .isPresent()) {
			throw new IllegalArgumentException("Replacement tag already exists");
		}
		var files = all ? fileTagRepository.findByTag(tag)
		                                   .stream()
		                                   .map(fileTag -> fileTag.getFile()
		                                                          .getId())
		                                   .distinct()
		                                   .toList()
		                : request.getFileIds();
		requireModify(fileRepository.findAllById(new LinkedHashSet<>(files)));
		tag.setTagName(request.getReplacementTagName());
		tag.setTagNameDe(request.getTagNameDe());
		tag.setTagNameEn(request.getTagNameEn());
		tag.setTagNameEs(request.getTagNameEs());
		tag.setTagNameFi(request.getTagNameFi());
		tag.setTagNameSv(request.getTagNameSv());
		tagRepository.save(tag);
		return files;
	}

	/**
	 * Renames the tag and rewrites the metadata of every tagged file as a background task (one step per file). The rename itself,
	 * the validation and the ACL checks run synchronously in this transaction; the task starts after it has committed.
	 */
	@Transactional
	public TaskProgress renameTagAcrossAllAsTask(TagOperationRequest request) {
		var before = tagRepository.findByTagName(request.getTagName())
								  .map(TagSnapshot::of)
								  .orElse(null);
		var          files      = prepareRename(request, true);
		var          proxy      = applicationContext.getBean(TagService.class);
		Compensation renameBack = before == null ? null : () -> proxy.restoreTag(request.getReplacementTagName(), before);
		return submitTagTask(TaskTypeEnum.TAG_RENAME_ACROSS_ALL,
							 "Rename tag '" + request.getTagName() + "' to '" + request.getReplacementTagName() + "' on " + files.size() + " files",
							 request, files, Operation.REPLACE, false, renameBack);
	}

	/**
	 * Undo of the tag rename done by {@link #renameTagAcrossAllAsTask}: puts the previous names back on the tag now called {@code currentName}.
	 */
	@Transactional
	public void restoreTag(String currentName, TagSnapshot before) {
		tagRepository.findByTagName(currentName)
					 .ifPresent(tag -> {
						 before.applyTo(tag);
						 tagRepository.save(tag);
					 });
	}

	private TaskProgress submitTagTask(TaskTypeEnum type, String title, TagOperationRequest request, List<Long> files, Operation operation,
									   boolean deleteUnusedTag, Compensation preparationUndo) {
		var proxy = applicationContext.getBean(TagService.class);
		return taskRunner.submit(type.name(), title, files.size(), progress -> {
			if (preparationUndo != null) {
				// Registered first so that it runs last, after the per-file metadata changes have been reverted
				progress.registerCompensation("Restore the tag names", preparationUndo);
			}
			proxy.applyTagOperation(request, files, operation, deleteUnusedTag, progress);
			return null;
		});
	}

	/**
	 * Undo of one file's metadata change made by a tag task: applies the inverse operation to the file.
	 */
	@Transactional
	public void revertTagMutation(long fileId, String oldTag, String newTag, Operation operation) {
		fileRepository.findById(fileId)
					  .ifPresent(file -> {
						  switch (operation) {
							  case ADD -> mutateFile(file, oldTag, null, Operation.REMOVE);
							  case REMOVE -> mutateFile(file, oldTag, null, Operation.ADD);
							  case REPLACE -> mutateFile(file, newTag, oldTag, Operation.REPLACE);
						  }
					  });
	}

	/**
	 * Transactional body of the tag background tasks.
	 */
	@Transactional
	public void applyTagOperation(TagOperationRequest request, List<Long> fileIds, Operation operation, boolean deleteUnusedTag, TaskProgress progress) {
		mutate(request, fileIds, operation, progress);
		if (deleteUnusedTag) {
			deleteTagIfUnused(request.getTagName());
		}
	}

	private void mutate(TagOperationRequest request, List<Long> fileIds, Operation operation, TaskProgress progress) {
		var oldTag = request.getTagName();
		var newTag = request.getReplacementTagName();
		var proxy  = progress == null ? null : applicationContext.getBean(TagService.class);
		for (var file : fileRepository.findAllById(new LinkedHashSet<>(fileIds))) {
			if (progress != null) {
				progress.checkpoint();
			}
			mutateFile(file, oldTag, newTag, operation);
			if (progress != null) {
				var fileId = file.getId();
				progress.registerCompensation("Restore tags of " + file.getFilename(), () -> proxy.revertTagMutation(fileId, oldTag, newTag, operation));
				progress.advance("Updated " + file.getFilename());
			}
		}
	}

	private void mutateFile(FileEntity file, String oldTag, String newTag, Operation operation) {
		try {
			var original = resolve(originalRootDirectory, file.getFilePath(), file.getFilename());
			if (!Files.isRegularFile(original.toPath())) {
				throw new IOException("Original file does not exist: " + original);
			}

			var metadata = MetadataTool.extractMetadataJsonObject(original);
			var subjects = new ArrayList<>(MetadataTool.extractSubjects(metadata));
			if (operation == Operation.ADD && !subjects.contains(oldTag)) {
				subjects.add(oldTag);
			} else if (operation == Operation.REMOVE) {
				subjects.removeIf(oldTag::equals);
			} else {
				subjects.removeIf(oldTag::equals);
				if (!subjects.contains(newTag)) {
					subjects.add(newTag);
				}
			}
			MetadataTool.writeSubjects(original, subjects);

			var export = exportFileRepository.findByFileId(file.getId())
											 .orElse(null);
			if (export != null) {
				var exported = resolve(exportRootDirectory, export.getFilePath(), export.getFilename());
				if (Files.isRegularFile(exported.toPath())) {
					MetadataTool.writeSubjects(exported, subjects);
					export.setFilesize(exported.length());
					export.setSha256sum(fi.poltsi.vempain.file.tools.FileTool.computeSha256(exported));
					exportFileRepository.save(export);
					regenerateThumbnail(export);
				}
			}
			directoryProcessorService.refreshExistingOriginalFile(file, original);
			file.setModifier(fi.poltsi.vempain.auth.tools.AuthTools.getCurrentUserId());
			file.setModified(Instant.now());
			fileRepository.save(file);
		} catch (IOException | VempainAuthenticationException e) {
			throw new IllegalStateException("Failed to update tags for file " + file.getId(), e);
		}
}

	private void requireModify(Iterable<FileEntity> files) {
		for (var file : files) {
			fileAclService.requireModify(file);
		}
	}

	private void regenerateThumbnail(fi.poltsi.vempain.file.entity.ExportFileEntity export) throws IOException {
		var thumbnailId = thumbFileRepository.findThumbnailIdByTargetFileId(export.getFile()
		                                                                          .getId());
		if (thumbnailId.isPresent()) {
			var thumbnail = thumbFileRepository.findById(thumbnailId.get())
			                                   .orElse(null);
			if (thumbnail != null) {
				var path = resolve(originalRootDirectory, thumbnail.getFilePath(), thumbnail.getFilename());
				Files.deleteIfExists(path.toPath());
				thumbFileRepository.delete(thumbnail);
			}
		}
		try {
			thumbnailGenerationService.generateThumbnail(export, originalRootDirectory, exportRootDirectory,
														 thumbnailMinimumSize, thumbnailQuality);
		} catch (Exception e) {
			throw new IOException("Failed to regenerate thumbnail", e);
		}
	}

	private List<Long> filesForTag(String tagName) {
		var tag = tagRepository.findByTagName(tagName)
							   .orElseThrow(() -> new IllegalArgumentException("Tag not found"));
		return fileTagRepository.findByTag(tag)
		                        .stream()
		                        .map(fileTag -> fileTag.getFile()
		                                               .getId())
		                        .distinct()
		                        .toList();
	}

	private TagEntity findOrCreateTag(String name) {
		return tagRepository.findByTagName(name)
		                    .orElseGet(() -> tagRepository.save(TagEntity.builder()
		                                                                 .tagName(name)
		                                                                 .build()));
	}

	private TagEntity requireExistingTag(String name) {
		return tagRepository.findByTagName(name)
							.orElseThrow(() -> new IllegalArgumentException("Replacement tag not found"));
	}

	private void deleteTag(String name) {
		tagRepository.findByTagName(name)
		             .ifPresent(tag -> {
						 fileTagRepository.deleteAll(fileTagRepository.findByTag(tag));
						 tagRepository.delete(tag);
					 });
	}

	private void deleteTagIfUnused(String name) {
		tagRepository.findByTagName(name)
		             .ifPresent(tag -> {
						 if (fileTagRepository.findByTag(tag)
			                                  .isEmpty()) {
							 tagRepository.delete(tag);
						 }
					 });
	}

	private void requireReplacement(TagOperationRequest request) {
		if (request.getReplacementTagName() == null || request.getReplacementTagName()
		                                                      .isBlank()) {
			throw new IllegalArgumentException("Replacement tag name must be provided");
		}
	}

	private File resolve(String root, String path, String filename) {
		return Path.of(root)
		           .resolve((path == null ? "" : path).replaceFirst("^/", ""))
		           .resolve(filename)
		           .toFile();
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
		return new TagResponse(entity.getId(), entity.getTagName(), entity.getTagNameDe(), entity.getTagNameEn(),
							   entity.getTagNameEs(), entity.getTagNameFi(), entity.getTagNameSv());
	}

	private Sort buildTagSort(String sortBy, Sort.Direction direction) {
		String property = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
			case "id" -> "id";
			case "tag_name" -> "tagName";
			case "tag_name_de" -> "tagNameDe";
			case "tag_name_en" -> "tagNameEn";
			case "tag_name_es" -> "tagNameEs";
			case "tag_name_fi" -> "tagNameFi";
			case "tag_name_sv" -> "tagNameSv";
			default -> "tagName";
		};
		return Sort.by(direction == null ? Sort.Direction.ASC : direction, property);
	}

	private Specification<TagEntity> buildTagSpecification(String search, boolean caseSensitive) {
		if (search == null || search.isBlank()) {
			return null;
		}
		var tokens = search.trim()
		                   .split("\\s+");
		var boundedTokens = LikePatterns.limitTokens(java.util.Arrays.asList(tokens));
		return (root, query, cb) -> cb.and(boundedTokens.stream()
		                                                   .map(token -> {
															   var pattern = caseSensitive ? LikePatterns.contains(token) : LikePatterns.containsIgnoreCase(token);
															   var fields  = List.of("tagName", "tagNameDe", "tagNameEn", "tagNameEs", "tagNameFi", "tagNameSv");
															   return cb.or(fields.stream()
			                                                                      .map(field -> {
																					  var path = root.<String>get(field);
																					  return caseSensitive ? cb.like(path, pattern, LikePatterns.ESCAPE_CHAR) : cb.like(cb.lower(path), pattern, LikePatterns.ESCAPE_CHAR);
																				  })
			                                                                      .toArray(jakarta.persistence.criteria.Predicate[]::new));
														   })
		                                                   .toArray(jakarta.persistence.criteria.Predicate[]::new));
	}

	enum Operation {ADD, REMOVE, REPLACE}

	/**
	 * Names of a tag before a rename, used to undo the rename of a cancelled task.
	 */
	public record TagSnapshot(String tagName, String tagNameDe, String tagNameEn, String tagNameEs, String tagNameFi, String tagNameSv) {
		static TagSnapshot of(TagEntity tag) {
			return new TagSnapshot(tag.getTagName(), tag.getTagNameDe(), tag.getTagNameEn(), tag.getTagNameEs(), tag.getTagNameFi(), tag.getTagNameSv());
		}

		void applyTo(TagEntity tag) {
			tag.setTagName(tagName);
			tag.setTagNameDe(tagNameDe);
			tag.setTagNameEn(tagNameEn);
			tag.setTagNameEs(tagNameEs);
			tag.setTagNameFi(tagNameFi);
			tag.setTagNameSv(tagNameSv);
		}
	}
}
