package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.entity.Unit;
import fi.poltsi.vempain.auth.security.AclAuthorizationService;
import fi.poltsi.vempain.auth.security.AclAuthorizationService.AclPrivilege;
import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the principal handling of {@link FileAclService}; the database backed behaviour is in {@code FileAclServiceITC}.
 */
@ExtendWith(MockitoExtension.class)
class FileAclServiceUTC {
	@Mock
	private AclAuthorizationService aclAuthorizationService;

	@InjectMocks
	private FileAclService fileAclService;

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void readableFilesIsBuiltForEveryKindOfPrincipal() {
		// anonymous
		assertThat(fileAclService.readableFiles()).isNotNull();

		// authenticated token whose principal is not a Vempain user
		SecurityContextHolder.getContext()
							 .setAuthentication(new UsernamePasswordAuthenticationToken("plain", "pw", List.of()));
		assertThat(fileAclService.readableFiles()).isNotNull();

		// unauthenticated token
		SecurityContextHolder.getContext()
							 .setAuthentication(new UsernamePasswordAuthenticationToken(user(1L, Set.of()), "pw"));
		assertThat(fileAclService.readableFiles()).isNotNull();

		// Vempain user without an id
		authenticate(user(null, Set.of()));
		assertThat(fileAclService.readableFiles()).isNotNull();

		// Vempain user with null units and with units lacking ids
		authenticate(user(1L, null));
		assertThat(fileAclService.readableFiles()).isNotNull();
		var units = new HashSet<Unit>();
		units.add(Unit.builder()
					  .build());
		units.add(Unit.builder()
					  .id(5L)
					  .build());
		authenticate(user(1L, units));
		assertThat(fileAclService.readableFiles((root, query, criteriaBuilder) -> null)).isNotNull();
	}

	@Test
	void helpersDelegateToTheSharedAuthorizationService() {
		authenticate(user(1L, Set.of()));
		var file = ImageFileEntity.builder()
								  .id(1L)
								  .aclId(10L)
								  .build();
		when(aclAuthorizationService.hasPrivilege(eq(10L), eq(AclPrivilege.READ), any())).thenReturn(true);
		when(aclAuthorizationService.hasPrivilege(eq(10L), eq(AclPrivilege.MODIFY), any())).thenReturn(true);
		when(aclAuthorizationService.hasPrivilege(eq(10L), eq(AclPrivilege.DELETE), any())).thenReturn(false);

		fileAclService.requireRead(file);
		fileAclService.requireModify(file);
		fileAclService.requireModify(List.of(file));
		assertThatThrownBy(() -> fileAclService.requireDelete(file)).isInstanceOf(AccessDeniedException.class)
																	.hasMessageContaining("delete");
		assertThat(fileAclService.canRead(file)).isTrue();
		assertThat(fileAclService.canModify(file)).isTrue();
		assertThat(fileAclService.canModifyAll(List.of(file))).isTrue();
	}

	@Test
	void nullFilesAreDeniedWithoutConsultingAcls() {
		assertThat(fileAclService.canRead(null)).isFalse();
		assertThat(fileAclService.canModify(null)).isFalse();
		assertThatThrownBy(() -> fileAclService.requireRead(null)).isInstanceOf(AccessDeniedException.class);
		fileAclService.requireModify((List<ImageFileEntity>) null);
		assertThat(fileAclService.canModifyAll(null)).isTrue();
		verify(aclAuthorizationService, never()).hasPrivilege(any(Long.class), any(), any());
	}

	private static void authenticate(UserDetailsImpl principal) {
		SecurityContextHolder.getContext()
							 .setAuthentication(new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities()));
	}

	private static UserDetailsImpl user(Long id, Set<Unit> units) {
		return new UserDetailsImpl(id, "login", "nick", "login@example.test", "pw", units, List.of());
	}
}
