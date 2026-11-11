package fi.poltsi.vempain.file.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.file.api.response.FileStatisticsResponse;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.TagEntity;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpReadServiceUTC {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private FileResponseEnricher fileResponseEnricher;
    @Mock
    private FileGroupRepository fileGroupRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private StatisticsService statisticsService;
    @Mock
    private AclService aclService;
    @InjectMocks
    private McpReadService mcpReadService;

    @Test
    void paginationRequiresNonNegativePageAndSizeFromOneThroughFiveHundred() {
        assertThrows(ResponseStatusException.class, () -> McpReadService.validatePagination(-1, 10));
        assertThrows(ResponseStatusException.class, () -> McpReadService.validatePagination(0, 0));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> McpReadService.validatePagination(0, 501)).getStatusCode());

        McpReadService.validatePagination(0, 1);
        McpReadService.validatePagination(7, 500);
    }

    @Test
    void invalidPagingIsRejectedBeforeAnyQuery() {
        assertThrows(ResponseStatusException.class, () -> mcpReadService.files(0, 501, null, null));
        assertThrows(ResponseStatusException.class, () -> mcpReadService.tags(-1, 10));
        assertThrows(ResponseStatusException.class, () -> mcpReadService.acls(0, 0));
        verifyNoInteractions(fileRepository, tagRepository, aclService);
    }

    @Test
    void fileTypeIsParsedCaseInsensitivelyAndUnknownTypesAreBadRequests() {
        assertNull(McpReadService.parseFileType(null));
        assertNull(McpReadService.parseFileType(" "));
        assertEquals(FileTypeEnum.IMAGE, McpReadService.parseFileType("image"));
        assertEquals(FileTypeEnum.VIDEO, McpReadService.parseFileType(" VIDEO "));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> McpReadService.parseFileType("photo")).getStatusCode());
    }

    @Test
    void filesArePagedByIdAndListedWithoutRawMetadata() {
        var entity = new ImageFileEntity();
        var response = new FileResponse();
        response.setMetadataRaw("{\"big\":\"json\"}");
        when(fileRepository.findAll(ArgumentMatchers.<Specification<FileEntity>>any(), any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<FileEntity>(List.of(entity), invocation.getArgument(1), 3));
        when(fileResponseEnricher.toResponses(List.of(entity))).thenReturn(List.of(response));

        var page = mcpReadService.files(1, 2, "image", "holiday");

        assertEquals(1, page.getPage());
        assertEquals(2, page.getSize());
        assertEquals(3, page.getTotalElements());
        assertEquals(1, page.getItems()
            .size());
        assertNull(page.getItems()
            .getFirst()
            .getMetadataRaw(), "the list never carries the raw metadata");
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(fileRepository).findAll(ArgumentMatchers.<Specification<FileEntity>>any(), pageable.capture());
        assertEquals(Sort.by(Sort.Direction.ASC, "id"), pageable.getValue()
            .getSort());
    }

    @Test
    void aSingleFileKeepsItsMetadataAndAMissingOneIsNotFound() {
        var entity = new ImageFileEntity();
        var response = new FileResponse();
        response.setMetadataRaw("{}");
        when(fileRepository.findById(5L)).thenReturn(Optional.of(entity));
        when(fileResponseEnricher.toResponse(entity)).thenReturn(response);
        when(fileRepository.findById(6L)).thenReturn(Optional.empty());

        assertEquals("{}", mcpReadService.file(5L)
            .getMetadataRaw());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> mcpReadService.file(6L)).getStatusCode());
    }

    @Test
    void aMissingFileGroupIsNotFound() {
        when(fileGroupRepository.findById(9L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> mcpReadService.fileGroup(9L)).getStatusCode());
    }

    @Test
    void tagsAreMappedFromTheRepositoryPage() {
        var tag = TagEntity.builder()
            .id(4L)
            .tagName("nature")
            .build();
        when(tagRepository.findAll(any(Pageable.class))).thenAnswer(invocation -> new PageImpl<>(List.of(tag), invocation.getArgument(0), 1));

        var page = mcpReadService.tags(0, 100);

        assertEquals(1, page.getTotalElements());
        assertEquals("nature", page.getItems()
            .getFirst()
            .getTagName());
    }

    @Test
    void statisticsAreTheSystemWideCounts() {
        var statistics = new FileStatisticsResponse();
        when(statisticsService.getStatistics()).thenReturn(statistics);

        assertSame(statistics, mcpReadService.statistics());
    }

    @Test
    void inMemoryPagingSlicesTheListAndStaysInRange() {
        var source = List.of(1, 2, 3, 4, 5);

        var second = McpReadService.paged(source, 1, 2);
        assertEquals(List.of(3, 4), second.getItems());
        assertEquals(5, second.getTotalElements());

        var beyond = McpReadService.paged(source, 9, 2);
        assertNotNull(beyond.getItems());
        assertTrue(beyond.getItems()
            .isEmpty());
        assertEquals(5, beyond.getTotalElements());
    }
}
