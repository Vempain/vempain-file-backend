package fi.poltsi.vempain.file.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The file group search builds its SQL from constants and binds every token as an escaped LIKE pattern (OWASP A05).
 */
class FileGroupRepositoryImplUTC {

	private final EntityManager           entityManager = mock(EntityManager.class);
	private final Query                   query         = mock(Query.class);
	private final FileGroupRepositoryImpl repository    = new FileGroupRepositoryImpl(entityManager);

	FileGroupRepositoryImplUTC() {
		when(entityManager.createNativeQuery(anyString())).thenReturn(query);
		when(query.getResultList()).thenReturn(List.of());
		when(query.getSingleResult()).thenReturn(0L);
	}

	@Test
	void tokensAreBoundAsEscapedPatternsWithAnExplicitEscapeClause() {
		repository.searchFileGroups("50% \"a_b\" Trip", false, PageRequest.of(0, 10));

		var sql = ArgumentCaptor.forClass(String.class);
		verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sql.capture());
		assertThat(sql.getAllValues()
					  .get(0)).contains("LOWER(fg.path) LIKE :term0 ESCAPE '\\'")
							  .doesNotContain("50%");
		verify(query, org.mockito.Mockito.times(2)).setParameter("term0", "%50\\%%");
		verify(query, org.mockito.Mockito.times(2)).setParameter("term1", "%a\\_b%");
		verify(query, org.mockito.Mockito.times(2)).setParameter("term2", "%trip%");
	}

	@Test
	void caseSensitiveSearchKeepsTheTextButStillEscapesIt() {
		repository.searchFileGroups("Trip_%", true, PageRequest.of(0, 10));

		var sql = ArgumentCaptor.forClass(String.class);
		verify(entityManager, org.mockito.Mockito.times(2)).createNativeQuery(sql.capture());
		assertThat(sql.getAllValues()
					  .get(0)).contains("fg.path LIKE :term0 ESCAPE '\\'");
		verify(query, org.mockito.Mockito.times(2)).setParameter("term0", "%Trip\\_\\%%");
	}
}
