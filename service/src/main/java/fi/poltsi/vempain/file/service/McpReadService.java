package fi.poltsi.vempain.file.service;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import fi.poltsi.vempain.auth.api.response.AclResponse;
import fi.poltsi.vempain.auth.api.response.UnitResponse;
import fi.poltsi.vempain.auth.api.response.UserResponse;
import fi.poltsi.vempain.auth.entity.Acl;
import fi.poltsi.vempain.auth.entity.Unit;
import fi.poltsi.vempain.auth.entity.UserAccount;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.auth.service.UnitService;
import fi.poltsi.vempain.auth.service.UserService;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.common.api.response.LocationResponse;
import fi.poltsi.vempain.file.api.response.FileGroupListResponse;
import fi.poltsi.vempain.file.api.response.FileGroupResponse;
import fi.poltsi.vempain.file.api.response.FileStatisticsResponse;
import fi.poltsi.vempain.file.api.response.McpPagedResponse;
import fi.poltsi.vempain.file.api.response.TagResponse;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.GpsLocationEntity;
import fi.poltsi.vempain.file.entity.TagEntity;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.LocationRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.tools.LikePatterns;
import lombok.RequiredArgsConstructor;

/**
 * The read-only, system-wide view of the file backend for vempain-mcp. Like the statistics these reads are deliberately not ACL-filtered: the caller is an
 * administrator's MCP_READ token, which only ever reaches GET endpoints. Every listing is ordered by id and paged with {@code page} from 0 and {@code size}
 * 1..500.
 */
@Service
@RequiredArgsConstructor
public class McpReadService {

    static final int MAX_PAGE_SIZE = 500;

    private final FileRepository fileRepository;
    private final FileResponseEnricher fileResponseEnricher;
    private final FileGroupRepository fileGroupRepository;
    private final TagRepository tagRepository;
    private final LocationRepository locationRepository;
    private final StatisticsService statisticsService;
    private final UserService userService;
    private final UnitService unitService;
    private final AclService aclService;

    /**
     * Files without their raw metadata, optionally of one type and/or containing {@code search} in the file name or path (case-insensitive, literal text).
     *
     * @throws ResponseStatusException 400 on an unknown file type or invalid paging
     */
    @Transactional(readOnly = true)
    public McpPagedResponse<FileResponse> files(int page, int size, String type, String search) {
        var pageable = pageable(page, size);
        var fileType = parseFileType(type);
        var result = fileRepository.findAll(fileSpecification(fileType, search), pageable);
        List<FileResponse> responses = fileResponseEnricher.toResponses(result.getContent());
        responses.forEach(response -> response.setMetadataRaw(null));
        return new McpPagedResponse<>(responses, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /**
     * One file with its type specific fields, thumbnail and raw metadata (the ExifTool JSON read at scan time).
     *
     * @throws ResponseStatusException 404 when there is no such file
     */
    @Transactional(readOnly = true)
    public FileResponse file(long id) {
        var entity = fileRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        return fileResponseEnricher.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public McpPagedResponse<FileGroupListResponse> fileGroups(int page, int size) {
        var result = fileGroupRepository.searchFileGroups(null, false, pageable(page, size));
        return mapped(result, row -> FileGroupListResponse.builder()
            .id(row.id())
            .path(row.path())
            .groupName(row.groupName())
            .description(row.description())
            .fileCount(row.fileCount())
            .exportedFileCount(row.exportedFileCount())
            .galleryId(row.galleryId())
            .build());
    }

    /**
     * One file group with all of its files (without their raw metadata).
     *
     * @throws ResponseStatusException 404 when there is no such file group
     */
    @Transactional(readOnly = true)
    public FileGroupResponse fileGroup(long id) {
        var entity = fileGroupRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File group not found"));
        var response = entity.toResponse();
        fileResponseEnricher.enrichAll(response.getFiles());
        return response;
    }

    @Transactional(readOnly = true)
    public McpPagedResponse<TagResponse> tags(int page, int size) {
        return mapped(tagRepository.findAll(pageable(page, size)), TagEntity::toResponse);
    }

    @Transactional(readOnly = true)
    public McpPagedResponse<LocationResponse> locations(int page, int size) {
        return mapped(locationRepository.findAll(pageable(page, size)), GpsLocationEntity::toResponse);
    }

    @Transactional(readOnly = true)
    public FileStatisticsResponse statistics() {
        return statisticsService.getStatistics();
    }

    @Transactional(readOnly = true)
    public McpPagedResponse<UserResponse> users(int page, int size) {
        validatePagination(page, size);
        var responses = StreamSupport.stream(userService.findAll()
            .spliterator(), false)
            .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
            .map(this::userResponse)
            .toList();
        return paged(responses, page, size);
    }

    @Transactional(readOnly = true)
    public McpPagedResponse<UnitResponse> units(int page, int size) {
        validatePagination(page, size);
        var responses = StreamSupport.stream(unitService.findAll()
            .spliterator(), false)
            .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
            .map(this::unitResponse)
            .toList();
        return paged(responses, page, size);
    }

    @Transactional(readOnly = true)
    public McpPagedResponse<AclResponse> acls(int page, int size) {
        validatePagination(page, size);
        var responses = aclService.findAll()
            .stream()
            .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
            .map(Acl::toResponse)
            .toList();
        return paged(responses, page, size);
    }

    /**
     * @throws ResponseStatusException 400 unless {@code page >= 0} and {@code 1 <= size <= 500}
     */
    static void validatePagination(int page, int size) {
        if (page < 0
            || size < 1
            || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    /**
     * @return the file type, or null when {@code type} is empty
     * @throws ResponseStatusException 400 when the text is no {@link FileTypeEnum} name
     */
    static FileTypeEnum parseFileType(String type) {
        if (type == null
            || type.isBlank()) {
            return null;
        }
        try {
            return FileTypeEnum.valueOf(type.trim()
                .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown file type: " + type);
        }
    }

    /**
     * Type equality and a literal, case-insensitive {@code LIKE} on the file name or the path; the search text is escaped by {@link LikePatterns}.
     */
    static Specification<FileEntity> fileSpecification(FileTypeEnum fileType, String search) {
        Specification<FileEntity> specification = (root, query, cb) -> cb.conjunction();
        if (fileType != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("fileType"), fileType));
        }
        if (search != null
            && !search.isBlank()) {
            var pattern = LikePatterns.containsIgnoreCase(search.trim());
            specification = specification.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("filename")), pattern, LikePatterns.ESCAPE_CHAR),
                    cb.like(cb.lower(root.get("filePath")), pattern, LikePatterns.ESCAPE_CHAR)));
        }
        return specification;
    }

    private static PageRequest pageable(int page, int size) {
        validatePagination(page, size);
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
    }

    private static <S, T> McpPagedResponse<T> mapped(Page<S> source, Function<S, T> mapper) {
        return new McpPagedResponse<>(source.getContent()
            .stream()
            .map(mapper)
            .toList(), source.getNumber(), source.getSize(), source.getTotalElements());
    }

    static <T> McpPagedResponse<T> paged(List<T> source, int page, int size) {
        var from = (int) Math.min((long) page * size, source.size());
        var to = Math.min(from + size, source.size());
        return new McpPagedResponse<>(source.subList(from, to), page, size, source.size());
    }

    private UserResponse userResponse(UserAccount user) {
        var response = user.getUserResponse();
        response.setUnitIds(user.getUnits()
            .stream()
            .map(Unit::getId)
            .sorted()
            .toList());
        response.setAcls(aclService.getAclResponses(user.getAclId()));
        return response;
    }

    private UnitResponse unitResponse(Unit unit) {
        var response = unit.getUnitResponse();
        unitService.populateWithMembers(response);
        response.setAcls(aclService.getAclResponses(unit.getAclId()));
        return response;
    }
}
