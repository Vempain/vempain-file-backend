package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.request.TagRequest;
import fi.poltsi.vempain.file.entity.FileTag;
import fi.poltsi.vempain.file.entity.TagEntity;
import fi.poltsi.vempain.file.repository.FileTagRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (UTC) for {@link TagService} — extended coverage of remaining branches.
 */
@ExtendWith(MockitoExtension.class)
class TagServiceExtendedUTC {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private FileTagRepository fileTagRepository;

    @InjectMocks
    private TagService tagService;

    @Nested
    @DisplayName("getTagById")
    class GetTagById {

        @Test
        void found_returnsMappedResponse() {
            var tag = TagEntity.builder().id(1L).tagName("nature").build();
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));

            var response = tagService.getTagById(1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getTagName()).isEqualTo("nature");
        }

        @Test
        void notFound_throwsException() {
            when(tagRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> tagService.getTagById(99L));
        }
    }

    @Nested
    @DisplayName("createTag")
    class CreateTag {

        @Test
        void savesAndReturnsResponse() {
            var request = new TagRequest(null, "urban", "urban", "urban", "urbano", "urbaani", "urban");
            var savedTag = TagEntity.builder().id(10L).tagName("urban").build();
            when(tagRepository.save(any())).thenReturn(savedTag);

            var response = tagService.createTag(request);

            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getTagName()).isEqualTo("urban");
        }

        @Test
        void savesAllLanguageFields() {
            var request = new TagRequest(null, "water", "Wasser", "water", "agua", "vesi", "vatten");
            var savedTag = TagEntity.builder().id(11L).tagName("water").tagNameDe("Wasser").tagNameEn("water").tagNameFi("vesi").build();
            when(tagRepository.save(any())).thenReturn(savedTag);

            var response = tagService.createTag(request);
            assertThat(response.getTagNameDe()).isEqualTo("Wasser");
        }
    }

    @Nested
    @DisplayName("updateTag")
    class UpdateTag {

        @Test
        void updatesExistingTag() {
            var existing = TagEntity.builder().id(5L).tagName("old").build();
            when(tagRepository.findById(5L)).thenReturn(Optional.of(existing));
            when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new TagRequest(5L, "new", "new", "new", "nuevo", "uusi", "ny");
            var response = tagService.updateTag(request);

            assertThat(response.getTagName()).isEqualTo("new");
        }

        @Test
        void nullId_throwsException() {
            var request = new TagRequest(null, "no-id", "no-id", "no-id", "no-id", "no-id", "no-id");

            assertThrows(IllegalArgumentException.class, () -> tagService.updateTag(request));
        }

        @Test
        void notFound_throwsException() {
            when(tagRepository.findById(50L)).thenReturn(Optional.empty());

            var request = new TagRequest(50L, "test", "test", "test", "test", "test", "test");
            assertThrows(IllegalArgumentException.class, () -> tagService.updateTag(request));
        }
    }

    @Nested
    @DisplayName("deleteTag")
    class DeleteTag {

        @Test
        void callsRepositoryDeleteById() {
            tagService.deleteTag(7L);
            verify(tagRepository).deleteById(7L);
        }
    }

    @Nested
    @DisplayName("getTagRequestsByFileId")
    class GetTagRequestsByFileId {

        @Test
        void returnsTagRequestsForFile() {
            var tag = TagEntity.builder().id(1L).tagName("sunset").build();
            var fileTag = FileTag.builder().tag(tag).build();
            when(fileTagRepository.findByFileId(100L)).thenReturn(List.of(fileTag));

            var result = tagService.getTagRequestsByFileId(100L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTagName()).isEqualTo("sunset");
        }

        @Test
        void filtersNullTags() {
            var fileTagWithNull = FileTag.builder().tag(null).build();
            when(fileTagRepository.findByFileId(200L)).thenReturn(List.of(fileTagWithNull));

            var result = tagService.getTagRequestsByFileId(200L);
            assertThat(result).isEmpty();
        }

        @Test
        void emptyFileTagList_returnsEmpty() {
            when(fileTagRepository.findByFileId(300L)).thenReturn(List.of());
            assertThat(tagService.getTagRequestsByFileId(300L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAllTags")
    class GetAllTags {

        @Test
        void returnsMappedTags() {
            var tag1 = TagEntity.builder().id(1L).tagName("tag1").build();
            var tag2 = TagEntity.builder().id(2L).tagName("tag2").build();
            when(tagRepository.findAll()).thenReturn(List.of(tag1, tag2));

            var result = tagService.getAllTags();
            assertThat(result).hasSize(2);
        }

        @Test
        void emptyRepository_returnsEmpty() {
            when(tagRepository.findAll()).thenReturn(List.of());
            assertThat(tagService.getAllTags()).isEmpty();
        }
    }
}
