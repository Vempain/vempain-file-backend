package fi.poltsi.vempain.file.service.files;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class FileSearchHelperUTC {

    @Test
    void buildSpecificationReturnsNullForEmptySearch() {
        assertThat(FileSearchHelper.buildSpecification(null, false)).isNull();
        assertThat(FileSearchHelper.buildSpecification("", false)).isNull();
        assertThat(FileSearchHelper.buildSpecification("   ", true)).isNull();
    }

    @Test
    void buildSpecificationReturnsSpecificationForNonEmptySearch() {
        var spec = FileSearchHelper.buildSpecification("holiday photo", false);
        assertThat(spec).isNotNull();
    }

    @Test
    void buildSortMapsKnownFields() {
        assertThat(FileSearchHelper.buildSort("id", Sort.Direction.DESC)
            .toString()).contains("id: DESC");
        assertThat(FileSearchHelper.buildSort("filename", Sort.Direction.ASC)
            .toString()).contains("filename: ASC");
        assertThat(FileSearchHelper.buildSort("file_path", Sort.Direction.ASC)
            .toString()).contains("filePath: ASC");
        assertThat(FileSearchHelper.buildSort("filepath", Sort.Direction.DESC)
            .toString()).contains("filePath: DESC");
        assertThat(FileSearchHelper.buildSort("description", Sort.Direction.ASC)
            .toString()).contains("description: ASC");
        assertThat(FileSearchHelper.buildSort("mimetype", Sort.Direction.ASC)
            .toString()).contains("mimetype: ASC");
        assertThat(FileSearchHelper.buildSort("filesize", Sort.Direction.ASC)
            .toString()).contains("filesize: ASC");
        assertThat(FileSearchHelper.buildSort("created", Sort.Direction.ASC)
            .toString()).contains("created: ASC");
        assertThat(FileSearchHelper.buildSort("modified", Sort.Direction.ASC)
            .toString()).contains("modified: ASC");
    }

    @Test
    void buildSortDefaultsToFilenameAscForUnknownInputs() {
        assertThat(FileSearchHelper.buildSort(null, null)
            .toString()).contains("filename: ASC");
        assertThat(FileSearchHelper.buildSort("unknown", null)
            .toString()).contains("filename: ASC");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildSpecificationEscapesWildcardsAndDeclaresTheEscapeCharacter() {
        jakarta.persistence.criteria.Root<fi.poltsi.vempain.file.entity.FileEntity> root = org.mockito.Mockito.mock();
        var cb = org.mockito.Mockito.mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        var path = org.mockito.Mockito.mock(jakarta.persistence.criteria.Path.class);
        var lower = org.mockito.Mockito.mock(jakarta.persistence.criteria.Expression.class);
        var predicate = org.mockito.Mockito.mock(jakarta.persistence.criteria.Predicate.class);
        org.mockito.Mockito.when(root.get(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(path);
        org.mockito.Mockito.when(cb.lower(org.mockito.ArgumentMatchers.any()))
            .thenReturn(lower);
        org.mockito.Mockito
            .when(cb.like(org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Expression<String>>any(), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyChar()))
            .thenReturn(predicate);
        org.mockito.Mockito.when(cb.or(org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Predicate>any()))
            .thenReturn(predicate);
        org.mockito.Mockito.when(cb.and(org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Predicate>any()))
            .thenReturn(predicate);

        FileSearchHelper.<fi.poltsi.vempain.file.entity.FileEntity>buildSpecification("50% Trip_", false)
            .toPredicate(root, null, cb);

        org.mockito.Mockito.verify(cb, org.mockito.Mockito.times(4))
            .like(lower, "%50\\%%", '\\');
        org.mockito.Mockito.verify(cb, org.mockito.Mockito.times(4))
            .like(lower, "%trip\\_%", '\\');
        org.mockito.Mockito.verify(cb, org.mockito.Mockito.never())
            .like(org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Expression<String>>any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void buildSpecificationUsesOnlyTheFirstTenTokens() {
        var manyTokens = java.util.stream.IntStream.range(0, 15)
            .mapToObj(i -> "t" + i)
            .collect(java.util.stream.Collectors.joining(" "));
        jakarta.persistence.criteria.Root<fi.poltsi.vempain.file.entity.FileEntity> root = org.mockito.Mockito.mock();
        var cb = org.mockito.Mockito.mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        var predicate = org.mockito.Mockito.mock(jakarta.persistence.criteria.Predicate.class);
        jakarta.persistence.criteria.Path<Object> path = org.mockito.Mockito.mock();
        org.mockito.Mockito.when(root.<Object>get(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(path);
        org.mockito.Mockito
            .when(cb.like(org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Expression<String>>any(), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyChar()))
            .thenReturn(predicate);
        org.mockito.Mockito.when(cb.or(org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Predicate>any()))
            .thenReturn(predicate);
        org.mockito.Mockito.when(cb.and(org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Predicate>any()))
            .thenReturn(predicate);

        FileSearchHelper.<fi.poltsi.vempain.file.entity.FileEntity>buildSpecification(manyTokens, true)
            .toPredicate(root, null, cb);

        // 4 fields per token, 10 tokens at most
        org.mockito.Mockito.verify(cb, org.mockito.Mockito.times(40))
            .like(org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Expression<String>>any(), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyChar());
    }
}
