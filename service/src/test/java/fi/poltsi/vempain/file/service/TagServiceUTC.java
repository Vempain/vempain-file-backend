package fi.poltsi.vempain.file.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import fi.poltsi.vempain.common.api.TaskStatusEnum;
import fi.poltsi.vempain.common.task.TaskProgress;
import fi.poltsi.vempain.common.task.TaskProgressStore;
import fi.poltsi.vempain.common.task.TaskRunner;
import fi.poltsi.vempain.file.api.request.TagOperationRequest;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileTag;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.TagEntity;
import fi.poltsi.vempain.file.entity.ThumbFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileTagRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.repository.files.ThumbFileRepository;
import fi.poltsi.vempain.file.tools.MetadataTool;
import fi.poltsi.vempain.file.tools.SampleFiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (UTC) for the tag mutation flows of {@link TagService}: the repositories and the ACL service are mocked, the files are real JPEGs under a
 * temporary directory so that the subjects ExifTool writes can be read back, and the background tasks run on a synchronous {@link TaskRunner}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagServiceUTC {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private FileTagRepository fileTagRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private FileResponseEnricher fileResponseEnricher;
    @Mock
    private ExportFileRepository exportFileRepository;
    @Mock
    private ThumbFileRepository thumbFileRepository;
    @Mock
    private DirectoryProcessorService directoryProcessorService;
    @Mock
    private ThumbnailGenerationService thumbnailGenerationService;
    @Mock
    private FileAclService fileAclService;
    @Mock
    private ApplicationContext applicationContext;

    @TempDir
    Path tempDir;

    private TagService tagService;
    private ImageFileEntity file;
    private File original;

    @BeforeEach
    void setUp() throws Exception {
        tagService = new TagService(tagRepository, fileTagRepository, fileRepository, fileResponseEnricher, exportFileRepository, thumbFileRepository,
                directoryProcessorService, thumbnailGenerationService, fileAclService, new TaskRunner(new TaskProgressStore(), Runnable::run),
                applicationContext);
        ReflectionTestUtils.setField(tagService, "originalRootDirectory", tempDir.toString());
        ReflectionTestUtils.setField(tagService, "exportRootDirectory", tempDir.resolve("export")
            .toString());
        ReflectionTestUtils.setField(tagService, "thumbnailMinimumSize", 100);
        ReflectionTestUtils.setField(tagService, "thumbnailQuality", 0.5f);
        when(applicationContext.getBean(TagService.class)).thenReturn(tagService);
        var principal = new UserDetailsImpl(1L, "admin", "Admin", "admin@nohost.nodomain", "Disabled", Set.of(), List.of());
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        original = SampleFiles.jpeg(tempDir.resolve("a.jpg"));
        MetadataTool.writeSubjects(original, List.of("old", "keep"));
        file = new ImageFileEntity();
        file.setId(1L);
        file.setFilePath("/");
        file.setFilename("a.jpg");
        when(fileRepository.findAllById(any())).thenReturn(List.of(file));
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exportFileRepository.findByFileId(any())).thenReturn(Optional.empty());
        when(tagRepository.findByTagName(anyString())).thenReturn(Optional.empty());
        when(tagRepository.save(any(TagEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileTagRepository.findByTag(any())).thenReturn(List.of());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private List<String> subjectsOf(File target) throws IOException {
        return MetadataTool.extractSubjects(MetadataTool.extractMetadataJsonObject(target));
    }

    private static TagOperationRequest request(String tagName, String replacement, List<Long> fileIds) {
        var request = new TagOperationRequest();
        request.setTagName(tagName);
        request.setReplacementTagName(replacement);
        request.setFileIds(fileIds);
        request.setTagNameFi(tagName + "-fi");
        return request;
    }

    private TagEntity existingTag(String name, FileEntity... taggedFiles) {
        var tag = TagEntity.builder()
            .id((long) name.hashCode())
            .tagName(name)
            .build();
        when(tagRepository.findByTagName(name)).thenReturn(Optional.of(tag));
        when(fileTagRepository.findByTag(tag)).thenReturn(java.util.Arrays.stream(taggedFiles)
            .map(tagged -> FileTag.builder()
                .file(tagged)
                .tag(tag)
                .build())
            .toList());
        return tag;
    }

    @Nested
    @DisplayName("synchronous mutations")
    class SynchronousMutations {

        @Test
        void addTagCreatesTheTagWritesItIntoTheFileAndRefreshesTheRow() throws Exception {
            tagService.addTag(request("fresh", null, List.of(1L)));

            assertThat(subjectsOf(original)).containsExactly("old", "keep", "fresh");
            var saved = ArgumentCaptor.forClass(TagEntity.class);
            verify(tagRepository, org.mockito.Mockito.times(2)).save(saved.capture());
            assertThat(saved.getValue()
                .getTagName()).isEqualTo("fresh");
            assertThat(saved.getValue()
                .getTagNameFi()).isEqualTo("fresh-fi");
            verify(fileAclService).requireModify(file);
            verify(directoryProcessorService).refreshExistingOriginalFile(file, original);
            verify(fileRepository).save(file);
            assertThat(file.getModifier()).isEqualTo(1L);
            assertThat(file.getModified()).isNotNull();
        }

        @Test
        void addTagDoesNotDuplicateAnExistingSubject() throws Exception {
            existingTag("keep");
            tagService.addTag(request("keep", null, List.of(1L)));
            assertThat(subjectsOf(original)).containsExactly("old", "keep");
        }

        @Test
        void removeTagRewritesTheFileAndDeletesAnUnusedTag() throws Exception {
            var tag = existingTag("old");

            tagService.removeTag(request("old", null, List.of(1L)), false);

            assertThat(subjectsOf(original)).containsExactly("keep");
            verify(tagRepository).delete(tag);
        }

        @Test
        void removeTagFromAllFilesUsesTheTaggedFilesAndKeepsATagStillInUse() throws Exception {
            var other = new ImageFileEntity();
            other.setId(2L);
            var tag = existingTag("old", file, other);
            when(fileRepository.findAllById(any())).thenReturn(List.of(file));

            tagService.removeTag(request("old", null, List.of()), true);

            assertThat(subjectsOf(original)).containsExactly("keep");
            verify(fileAclService).requireModify(file);
            verify(tagRepository, never()).delete(tag);
        }

        @Test
        void removeTagOfAnUnknownTagFails() {
            assertThatThrownBy(() -> tagService.removeTag(request("nope", null, List.of()), true)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tag not found");
        }

        @Test
        void replaceTagNeedsAnExistingReplacementAndRewritesTheFile() throws Exception {
            assertThatThrownBy(() -> tagService.replaceTag(request("old", " ", List.of(1L)), false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Replacement tag name must be provided");
            assertThatThrownBy(() -> tagService.replaceTag(request("old", "missing", List.of(1L)), false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Replacement tag not found");

            existingTag("keep", file);
            tagService.replaceTag(request("old", "keep", List.of(1L)), false);
            assertThat(subjectsOf(original)).containsExactly("keep");

            existingTag("brand-new", file);
            tagService.replaceTag(request("keep", "brand-new", List.of(1L)), true);
            assertThat(subjectsOf(original)).containsExactly("brand-new");
        }

        @Test
        void renameTagRenamesTheEntityAndRewritesTheFile() throws Exception {
            var tag = existingTag("old", file);
            var request = request("old", "renamed", List.of(1L));
            request.setTagNameDe("de");
            request.setTagNameEn("en");
            request.setTagNameEs("es");
            request.setTagNameSv("sv");

            tagService.renameTag(request, false);

            assertThat(tag.getTagName()).isEqualTo("renamed");
            assertThat(tag.getTagNameDe()).isEqualTo("de");
            assertThat(tag.getTagNameSv()).isEqualTo("sv");
            assertThat(subjectsOf(original)).containsExactly("keep", "renamed");
            verify(tagRepository).lockTagMutations();
            verify(tagRepository).save(tag);
        }

        @Test
        void renameTagRefusesUnknownTagsAndExistingReplacementsButAcceptsTheSameName() throws Exception {
            assertThatThrownBy(() -> tagService.renameTag(request("nope", "x", List.of(1L)), false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tag not found");

            existingTag("old", file);
            existingTag("taken");
            assertThatThrownBy(() -> tagService.renameTag(request("old", "taken", List.of(1L)), false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Replacement tag already exists");

            tagService.renameTag(request("old", "old", List.of()), true);
            assertThat(subjectsOf(original)).containsExactly("keep", "old");
        }

        @Test
        void deniedFileStopsTheMutationBeforeAnyFileIsTouched() throws Exception {
            doThrow(new AccessDeniedException("denied")).when(fileAclService)
                .requireModify(file);

            assertThatThrownBy(() -> tagService.addTag(request("fresh", null, List.of(1L)))).isInstanceOf(AccessDeniedException.class);

            assertThat(subjectsOf(original)).containsExactly("old", "keep");
            verify(directoryProcessorService, never()).refreshExistingOriginalFile(any(), any());
        }

        @Test
        void deleteTagRequiresModifyOnTheTaggedFiles() {
            var tag = TagEntity.builder()
                .id(7L)
                .tagName("t")
                .files(Set.of(file))
                .build();
            when(tagRepository.findById(7L)).thenReturn(Optional.of(tag));

            tagService.deleteTag(7L);

            verify(fileAclService).requireModify(file);
            verify(tagRepository).deleteById(7L);
        }
    }

    @Nested
    @DisplayName("file rewrite details")
    class FileRewrite {

        @Test
        void exportAndThumbnailFollowTheOriginal() throws Exception {
            var exportDir = Files.createDirectories(tempDir.resolve("export"));
            var exported = SampleFiles.jpeg(exportDir.resolve("a.jpg"));
            var export = ExportFileEntity.builder()
                .id(3L)
                .file(file)
                .filePath("/")
                .filename("a.jpg")
                .sha256sum("stale")
                .build();
            when(exportFileRepository.findByFileId(1L)).thenReturn(Optional.of(export));
            var thumbnail = new ThumbFileEntity();
            thumbnail.setFilePath("/thumb");
            thumbnail.setFilename("a.jpeg");
            var thumbnailFile = Files.createDirectories(tempDir.resolve("thumb"))
                .resolve("a.jpeg");
            Files.writeString(thumbnailFile, "thumb");
            when(thumbFileRepository.findThumbnailIdByTargetFileId(1L)).thenReturn(Optional.of(9L));
            when(thumbFileRepository.findById(9L)).thenReturn(Optional.of(thumbnail));

            tagService.addTag(request("fresh", null, List.of(1L)));

            assertThat(subjectsOf(exported)).containsExactly("old", "keep", "fresh");
            assertThat(export.getSha256sum()).isNotEqualTo("stale");
            assertThat(export.getFilesize()).isEqualTo(exported.length());
            assertThat(thumbnailFile).doesNotExist();
            verify(thumbFileRepository).delete(thumbnail);
            verify(exportFileRepository).save(export);
            verify(thumbnailGenerationService).generateThumbnail(export, tempDir.toString(), tempDir.resolve("export")
                .toString(), 100, 0.5f);
        }

        @Test
        void missingThumbnailRowAndMissingExportFileAreSkipped() throws Exception {
            var export = ExportFileEntity.builder()
                .id(3L)
                .file(file)
                .filePath("/")
                .filename("gone.jpg")
                .build();
            when(exportFileRepository.findByFileId(1L)).thenReturn(Optional.of(export));
            tagService.addTag(request("fresh", null, List.of(1L)));
            verify(exportFileRepository, never()).save(any());

            var exportDir = Files.createDirectories(tempDir.resolve("export"));
            SampleFiles.jpeg(exportDir.resolve("gone.jpg"));
            when(thumbFileRepository.findThumbnailIdByTargetFileId(1L)).thenReturn(Optional.of(9L));
            when(thumbFileRepository.findById(9L)).thenReturn(Optional.empty());
            tagService.addTag(request("second", null, List.of(1L)));
            verify(thumbFileRepository, never()).delete(any(ThumbFileEntity.class));
            verify(thumbnailGenerationService).generateThumbnail(any(), any(), any(), any(Integer.class), any(Float.class));
        }

        @Test
        void thumbnailFailureFailsTheMutation() throws Exception {
            var exportDir = Files.createDirectories(tempDir.resolve("export"));
            SampleFiles.jpeg(exportDir.resolve("a.jpg"));
            var export = ExportFileEntity.builder()
                .id(3L)
                .file(file)
                .filePath("/")
                .filename("a.jpg")
                .build();
            when(exportFileRepository.findByFileId(1L)).thenReturn(Optional.of(export));
            when(thumbFileRepository.findThumbnailIdByTargetFileId(1L)).thenReturn(Optional.empty());
            doThrow(new IllegalStateException("no thumbnail")).when(thumbnailGenerationService)
                .generateThumbnail(any(), any(), any(), any(Integer.class), any(Float.class));

            assertThatThrownBy(() -> tagService.addTag(request("fresh", null, List.of(1L)))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to update tags for file 1")
                .hasCauseInstanceOf(IOException.class);
        }

        @Test
        void missingOriginalOrMissingAuthenticationFailTheMutation() {
            file.setFilename("missing.jpg");
            assertThatThrownBy(() -> tagService.addTag(request("fresh", null, List.of(1L)))).isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(IOException.class);

            file.setFilename("a.jpg");
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> tagService.addTag(request("fresh", null, List.of(1L)))).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("background tasks")
    class BackgroundTasks {

        @Test
        void removeTagFromAllRunsOneStepPerFileAndDeletesTheUnusedTag() throws Exception {
            var tag = existingTag("old", file);
            // the file_tags rows are rebuilt by the (mocked) refresh of the file: the tag is unused once the file has been rewritten
            when(fileTagRepository.findByTag(tag)).thenReturn(List.of(FileTag.builder()
                .file(file)
                .tag(tag)
                .build()))
                .thenReturn(List.of());

            var task = tagService.removeTagFromAllAsTask(request("old", null, List.of()));

            assertThat(task.getType()).isEqualTo("TAG_REMOVE_FROM_ALL");
            assertThat(task.getTitle()).isEqualTo("Remove tag 'old' from 1 files");
            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
            assertThat(task.getCompletedSteps()
                .get()).isEqualTo(1L);
            assertThat(task.getMessage()).isEqualTo("Updated a.jpg");
            assertThat(subjectsOf(original)).containsExactly("keep");
            verify(tagRepository).delete(tag);
        }

        @Test
        void replaceTagAcrossAllRewritesEveryTaggedFile() throws Exception {
            existingTag("old", file);
            existingTag("keep", file);

            var task = tagService.replaceTagAcrossAllAsTask(request("old", "keep", List.of()));

            assertThat(task.getType()).isEqualTo("TAG_REPLACE_ACROSS_ALL");
            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
            assertThat(subjectsOf(original)).containsExactly("keep");
        }

        @Test
        void aFailingFileRevertsTheFilesAlreadyRewrittenAndTheRename() throws Exception {
            var tag = existingTag("old", file);
            tag.setTagNameFi("vanha");
            var broken = new ImageFileEntity();
            broken.setId(2L);
            broken.setFilePath("/");
            broken.setFilename("broken.jpg");
            when(fileRepository.findAllById(any())).thenReturn(List.of(file, broken));
            when(fileRepository.findById(2L)).thenReturn(Optional.of(broken));
            when(fileTagRepository.findByTag(tag)).thenReturn(List.of(FileTag.builder()
                .file(file)
                .tag(tag)
                .build(),
                    FileTag.builder()
                        .file(broken)
                        .tag(tag)
                        .build()));

            when(tagRepository.findByTagName("renamed")).thenAnswer(invocation -> "renamed".equals(tag.getTagName()) ? Optional.of(tag) : Optional.empty());

            var task = tagService.renameTagAcrossAllAsTask(request("old", "renamed", List.of()));

            assertThat(task.getType()).isEqualTo("TAG_RENAME_ACROSS_ALL");
            assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.FAILED);
            assertThat(task.getErrorMessage()).contains("Failed to update tags for file 2");
            assertThat(task.getRevertedSteps()
                .get()).isEqualTo(2L);
            // the first file was rewritten and then reverted, the rename was undone through the snapshot
            assertThat(subjectsOf(original)).containsExactly("keep", "old");
            assertThat(tag.getTagName()).isEqualTo("old");
            assertThat(tag.getTagNameFi()).isEqualTo("vanha");
        }

        @Test
        void renameTaskRefusesAnUnknownTagBeforeStarting() {
            assertThatThrownBy(() -> tagService.renameTagAcrossAllAsTask(request("nope", "x", List.of()))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void durableWorkerEntryPointAppliesTheOperation() throws Exception {
            var progress = TaskProgress.unmanaged("t", "TAG_REMOVE_FROM_ALL", "title", 1L, 1, "{}");
            existingTag("old", file);

            tagService.applyTagOperationForTask(request("old", null, List.of(1L)), List.of(1L), "REMOVE", true, progress);

            assertThat(subjectsOf(original)).containsExactly("keep");
            assertThat(progress.getCompletedSteps()
                .get()).isEqualTo(1L);
        }

        @Test
        void revertTagMutationAppliesTheInverseOperation() throws Exception {
            tagService.revertTagMutation(1L, "keep", "", "ADD");
            assertThat(subjectsOf(original)).containsExactly("old");

            tagService.revertTagMutation(1L, "keep", "", "REMOVE");
            assertThat(subjectsOf(original)).containsExactly("old", "keep");

            MetadataTool.writeSubjects(original, List.of("keep", "new"));
            tagService.revertTagMutation(1L, "old", "new", "REPLACE");
            assertThat(subjectsOf(original)).containsExactly("keep", "old");

            tagService.revertTagMutation(404L, "old", "new", "REPLACE");
            verify(fileRepository).findById(404L);
        }

        @Test
        void restoreTagPutsTheSnapshotBack() {
            var tag = existingTag("renamed");
            tag.setTagNameEn("en");
            var before = new TagService.TagSnapshot("old", "de", null, "es", "fi", "sv");

            tagService.restoreTag("renamed", before);

            assertThat(tag.getTagName()).isEqualTo("old");
            assertThat(tag.getTagNameDe()).isEqualTo("de");
            assertThat(tag.getTagNameEn()).isNull();
            assertThat(tag.getTagNameEs()).isEqualTo("es");
            assertThat(tag.getTagNameFi()).isEqualTo("fi");
            assertThat(tag.getTagNameSv()).isEqualTo("sv");
            verify(tagRepository).save(tag);

            tagService.restoreTag("unknown", before);
            verify(tagRepository, org.mockito.Mockito.times(1)).save(any(TagEntity.class));
        }
    }

    @Nested
    @DisplayName("listings")
    class Listings {

        @Test
        @SuppressWarnings("unchecked")
        void filesByTagAreAclFilteredAndSupportASearch() {
            when(tagRepository.existsById(5L)).thenReturn(true);
            Specification<FileEntity> readable = (root, query, cb) -> cb.conjunction();
            when(fileAclService.<FileEntity>readableFiles(any())).thenReturn(readable);
            var pageable = ArgumentCaptor.forClass(Pageable.class);
            when(fileRepository.findAll(any(Specification.class), pageable.capture())).thenReturn(new PageImpl<>(List.of(file)));
            var response = new FileResponse();
            response.setId(1L);
            when(fileResponseEnricher.<FileResponse>toResponses(List.of(file))).thenReturn(List.of(response));

            var withSearch = tagService.getFilesByTag(5L, new PagedRequest(-1, 500, "filename", Sort.Direction.DESC, "holiday", true));
            var plain = tagService.getFilesByTag(5L, new PagedRequest(0, 10, null, null, null, null));

            assertThat(withSearch.getContent()).extracting(FileResponse::getId)
                .containsExactly(1L);
            assertThat(plain.getContent()).hasSize(1);
            assertThat(pageable.getAllValues()
                .getFirst()
                .getPageNumber()).isZero();
            assertThat(pageable.getAllValues()
                .getFirst()
                .getPageSize()).isEqualTo(200);
            verify(fileAclService, org.mockito.Mockito.times(2)).readableFiles(any());
        }

        @Test
        @SuppressWarnings("unchecked")
        void tagListingMapsEverySortColumn() {
            var pageable = ArgumentCaptor.forClass(Pageable.class);
            when(tagRepository.findAll(org.mockito.ArgumentMatchers.nullable(Specification.class), pageable.capture())).thenReturn(new PageImpl<>(List.of()));

            for (var sortBy : List.of("id", "tag_name", "tag_name_de", "tag_name_en", "tag_name_es", "tag_name_fi", "tag_name_sv", "bogus")) {
                tagService.getAllTagsPageable(new PagedRequest(0, 10, sortBy, Sort.Direction.DESC, "a b", true));
            }
            tagService.getAllTagsPageable(new PagedRequest(0, 10, null, null, null, null));

            var properties = pageable.getAllValues()
                .stream()
                .map(page -> page.getSort()
                    .iterator()
                    .next()
                    .getProperty())
                .toList();
            assertThat(properties).containsExactly("id", "tagName", "tagNameDe", "tagNameEn", "tagNameEs", "tagNameFi", "tagNameSv", "tagName", "tagName");
            assertThat(pageable.getAllValues()
                .getLast()
                .getSort()
                .iterator()
                .next()
                .getDirection()).isEqualTo(Sort.Direction.ASC);
        }
    }
}
