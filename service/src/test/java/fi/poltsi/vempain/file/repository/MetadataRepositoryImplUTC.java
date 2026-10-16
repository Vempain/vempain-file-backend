package fi.poltsi.vempain.file.repository;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The metadata summary search builds its SQL from constants, binds every filter as an escaped LIKE pattern and maps the sort column through an allow-list
 * (OWASP A05).
 */
class MetadataRepositoryImplUTC {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final Query query = mock(Query.class);
    private final MetadataRepositoryImpl repository = new MetadataRepositoryImpl(entityManager);

    MetadataRepositoryImplUTC() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(new Object[]{ "XMP-dc", "Creator", "Jane", 3L }, new Object[]{ null, null, null, null }));
        when(query.getSingleResult()).thenReturn(2L);
    }

    @Test
    void filtersAreBoundAsEscapedPatternsAndRowsAreMapped() {
        var page = repository.searchSummaries("xmp_", " ", "50%", false, PageRequest.of(0, 10));

        var sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sql.capture());
        assertThat(sql.getAllValues()
            .get(0)).contains("LOWER(m.metadata_group) LIKE :metadata_group ESCAPE '\\'")
            .contains(" AND LOWER(m.metadata_value) LIKE :metadata_value ESCAPE '\\'")
            .doesNotContain("metadata_key LIKE")
            .contains(" ORDER BY m.metadata_group ASC, m.metadata_key ASC, m.metadata_value ASC")
            .contains("OFFSET :offset LIMIT :limit");
        verify(query, org.mockito.Mockito.times(2)).setParameter("metadata_group", "%xmp\\_%");
        verify(query, org.mockito.Mockito.times(2)).setParameter("metadata_value", "%50\\%%");
        verify(query, never()).setParameter(org.mockito.ArgumentMatchers.eq("metadata_key"), org.mockito.ArgumentMatchers.any());
        verify(query).setParameter("offset", 0);
        verify(query).setParameter("limit", 10);
        assertThat(page.getTotalElements()).isEqualTo(2L);
        assertThat(page.getContent()
            .getFirst()
            .metadataGroup()).isEqualTo("XMP-dc");
        assertThat(page.getContent()
            .getFirst()
            .fileCount()).isEqualTo(3L);
        assertThat(page.getContent()
            .get(1)
            .metadataGroup()).isNull();
        assertThat(page.getContent()
            .get(1)
            .fileCount()).isZero();
    }

    @Test
    void caseSensitiveSearchKeepsTheTextAndSortsThroughTheAllowList() {
        repository.searchSummaries("XMP", null, null, true, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "group")));

        var sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sql.capture());
        assertThat(sql.getAllValues()
            .get(0)).contains("WHERE m.metadata_group LIKE :metadata_group ESCAPE '\\'")
            .contains(" ORDER BY m.metadata_group DESC, m.metadata_group ASC, m.metadata_key ASC, m.metadata_value ASC");
        verify(query, org.mockito.Mockito.times(2)).setParameter("metadata_group", "%XMP%");
        assertThat(sql.getAllValues()
            .get(1)).startsWith("SELECT COUNT(*) FROM (SELECT 1 FROM metadata m WHERE")
            .doesNotContain("ORDER BY");
    }

    @Test
    void sortColumnsMapToTheKnownColumnsOnly() {
        assertThat(MetadataRepositoryImpl.mapSort("metadata_group")).isEqualTo("m.metadata_group");
        assertThat(MetadataRepositoryImpl.mapSort("metadataGroup")).isEqualTo("m.metadata_group");
        assertThat(MetadataRepositoryImpl.mapSort("value")).isEqualTo("m.metadata_value");
        assertThat(MetadataRepositoryImpl.mapSort("metadata_value")).isEqualTo("m.metadata_value");
        assertThat(MetadataRepositoryImpl.mapSort("FILE_COUNT")).isEqualTo("file_count");
        assertThat(MetadataRepositoryImpl.mapSort("metadata_key")).isEqualTo("m.metadata_key");
        assertThat(MetadataRepositoryImpl.mapSort("1; DROP TABLE metadata")).isEqualTo("m.metadata_key");
        assertThat(MetadataRepositoryImpl.mapSort(null)).isEqualTo("m.metadata_key");
    }
}
