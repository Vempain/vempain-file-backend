package fi.poltsi.vempain.file.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.tools.LikePatterns;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MetadataRepositoryImpl implements MetadataRepositoryCustom {

    private final EntityManager entityManager;

    private static MetadataSummaryRow mapRow(Object[] tuple) {
        var group = tuple[0] != null ? tuple[0].toString() : null;
        var key = tuple[1] != null ? tuple[1].toString() : null;
        var value = tuple[2] != null ? tuple[2].toString() : null;
        var count = tuple[3] != null ? ((Number) tuple[3]).longValue() : 0L;
        return new MetadataSummaryRow(group, key, value, count);
    }

    @Override
    public Page<MetadataSummaryRow> searchSummaries(String groupFilter, String keyFilter, String valueFilter, boolean caseSensitive, Pageable pageable) {
        Map<String, String> filters = new LinkedHashMap<>();
        addFilter(filters, "metadata_group", groupFilter);
        addFilter(filters, "metadata_key", keyFilter);
        addFilter(filters, "metadata_value", valueFilter);

        var whereClause = buildWhereClause(filters.keySet(), caseSensitive);
        var groupClause = " GROUP BY m.metadata_group, m.metadata_key, m.metadata_value";

        var selectSql = "SELECT m.metadata_group, m.metadata_key, m.metadata_value, COUNT(DISTINCT m.file_id) AS file_count FROM metadata m"
                + whereClause
                + groupClause
                + buildOrderClause(pageable)
                + " OFFSET :offset LIMIT :limit";
        log.debug("Metadata summary SQL: {}", selectSql);
        Query dataQuery = entityManager.createNativeQuery(selectSql);
        bindParameters(dataQuery, filters, caseSensitive);
        dataQuery.setParameter("offset", (int) pageable.getOffset());
        dataQuery.setParameter("limit", pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Object[]> rawRows = dataQuery.getResultList();
        var rows = rawRows.stream()
            .map(MetadataRepositoryImpl::mapRow)
            .toList();

        var countSql = "SELECT COUNT(*) FROM (SELECT 1 FROM metadata m" + whereClause + groupClause + ") AS summaries";
        log.debug("Metadata summary count SQL: {}", countSql);
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindParameters(countQuery, filters, caseSensitive);
        var total = (Number) countQuery.getSingleResult();

        return new PageImpl<>(rows, pageable, total.longValue());
    }

    private static void addFilter(Map<String, String> filters, String column, String text) {
        if (text != null
            && !text.isBlank()) {
            filters.put(column, text.trim());
        }
    }

    private String buildWhereClause(Iterable<String> columns, boolean caseSensitive) {
        var clauses = new ArrayList<String>();
        for (var column : columns) {
            // Request text is escaped by LikePatterns; the escape character is declared explicitly so the pattern is literal text
            clauses.add((caseSensitive ? "m." + column : "LOWER(m." + column + ")") + " LIKE :" + column + LikePatterns.ESCAPE_CLAUSE);
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    private void bindParameters(Query query, Map<String, String> filters, boolean caseSensitive) {
        filters.forEach((column, text) -> query.setParameter(column, caseSensitive ? LikePatterns.contains(text) : LikePatterns.containsIgnoreCase(text)));
    }

    private String buildOrderClause(Pageable pageable) {
        if (!pageable.getSort()
            .isSorted()) {
            return " ORDER BY m.metadata_group ASC, m.metadata_key ASC, m.metadata_value ASC";
        }
        var orders = new ArrayList<String>();
        for (var order : pageable.getSort()) {
            orders.add(mapSort(order.getProperty())
                    + " "
                    + order.getDirection()
                        .name());
        }
        // Secondary keys keep the paging stable when the sorted column has duplicates
        orders.add("m.metadata_group ASC");
        orders.add("m.metadata_key ASC");
        orders.add("m.metadata_value ASC");
        return " ORDER BY " + String.join(", ", orders);
    }

    /**
     * Maps a request sort column to a SQL expression; unknown names fall back to the key so that request text never reaches the SQL.
     */
    static String mapSort(String property) {
        return switch (property == null ? "" : property.toLowerCase()) {
            case "metadata_group", "metadatagroup", "group" -> "m.metadata_group";
            case "metadata_value", "metadatavalue", "value" -> "m.metadata_value";
            case "file_count", "filecount" -> "file_count";
            default -> "m.metadata_key";
        };
    }
}
