package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.entity.Acl;
import fi.poltsi.vempain.auth.entity.Unit;
import fi.poltsi.vempain.auth.security.AclAuthorizationService;
import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import fi.poltsi.vempain.file.entity.FileEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resource authorization for {@link FileEntity} rows. Files are the only ACL-linked resources in this service: every file carries an
 * {@code acl_id} and access is granted only when an ACL row for that id gives the authenticated user, or one of the user's units, the
 * requested privilege. A file whose {@code acl_id} is not positive, or whose ACL rows are missing, is denied for everyone (fail closed);
 * {@code FileAclRepairSchedule} re-creates the missing ACL for the creator once a day.
 */
@Service
@RequiredArgsConstructor
public class FileAclService {
	private final AclAuthorizationService aclAuthorizationService;

	/**
	 * Specification that keeps only the files the current user may read. Used by every paged listing so that paging and counting are
	 * evaluated in the database.
	 */
	public <T extends FileEntity> Specification<T> readableFiles() {
		var access = currentAclAccess();
		return (root, query, criteriaBuilder) ->
		{
			if (access.userId() == null) {
				// No authenticated Vempain user: nothing is readable
				return criteriaBuilder.disjunction();
			}

			var accessibleAclIds  = query.subquery(Long.class);
			var accessibleAclRoot = accessibleAclIds.from(Acl.class);
			accessibleAclIds.select(accessibleAclRoot.get("aclId"));
			accessibleAclIds.where(
					criteriaBuilder.equal(accessibleAclRoot.get("aclId"), root.get("aclId")),
					criteriaBuilder.isTrue(accessibleAclRoot.get("readPrivilege")),
					accessiblePrincipalPredicate(accessibleAclRoot, access, criteriaBuilder)
			);

			return criteriaBuilder.and(
					criteriaBuilder.greaterThan(root.get("aclId"), 0L),
					criteriaBuilder.exists(accessibleAclIds)
			);
		};
	}

	public <T extends FileEntity> Specification<T> readableFiles(Specification<T> search) {
		Specification<T> readable = this.readableFiles();
		return search == null ? readable : readable.and(search);
	}

	public void requireRead(FileEntity file) {
		require(file, AclAuthorizationService.AclPrivilege.READ);
	}

	public void requireModify(FileEntity file) {
		require(file, AclAuthorizationService.AclPrivilege.MODIFY);
	}

	public void requireModify(Collection<? extends FileEntity> files) {
		if (files == null) {
			return;
		}

		for (var file : files) {
			requireModify(file);
		}
	}

	public void requireDelete(FileEntity file) {
		require(file, AclAuthorizationService.AclPrivilege.DELETE);
	}

	public boolean canRead(FileEntity file) {
		return hasPrivilege(file, AclAuthorizationService.AclPrivilege.READ);
	}

	public boolean canModify(FileEntity file) {
		return hasPrivilege(file, AclAuthorizationService.AclPrivilege.MODIFY);
	}

	/**
	 * @return {@code true} when the current user may modify every file in the collection. An empty collection is allowed because there
	 * is nothing to protect.
	 */
	public boolean canModifyAll(Collection<? extends FileEntity> files) {
		if (files == null || files.isEmpty()) {
			return true;
		}

		return files.stream()
					.allMatch(this::canModify);
	}

	private boolean hasPrivilege(FileEntity file, AclAuthorizationService.AclPrivilege privilege) {
		return file != null
			   && aclAuthorizationService.hasPrivilege(file.getAclId(), privilege, SecurityContextHolder.getContext()
																										.getAuthentication());
	}

	private void require(FileEntity file, AclAuthorizationService.AclPrivilege privilege) {
		if (!hasPrivilege(file, privilege)) {
			throw new AccessDeniedException("The current user has no " + privilege.name()
																				  .toLowerCase() + " access to the file");
		}
	}

	private Predicate accessiblePrincipalPredicate(Root<Acl> aclRoot,
												   AclAccess access,
												   CriteriaBuilder criteriaBuilder) {
		var predicates = new ArrayList<Predicate>();
		predicates.add(criteriaBuilder.equal(aclRoot.get("userId"), access.userId()));
		if (!access.unitIds()
		           .isEmpty()) {
			predicates.add(criteriaBuilder.and(
					criteriaBuilder.isNull(aclRoot.get("userId")),
					aclRoot.get("unitId")
					       .in(access.unitIds())
			));
		}
		return criteriaBuilder.or(predicates.toArray(Predicate[]::new));
	}

	private AclAccess currentAclAccess() {
		var authentication = SecurityContextHolder.getContext()
												  .getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
			|| !(authentication.getPrincipal() instanceof UserDetailsImpl user)
			|| user.getId() == null) {
			return new AclAccess(null, Set.of());
		}

		var unitIds = user.getUnits() == null
					  ? Set.<Long>of()
					  : user.getUnits()
							.stream()
							.map(Unit::getId)
							.filter(Objects::nonNull)
							.collect(Collectors.toSet());
		return new AclAccess(user.getId(), unitIds);
	}

	private record AclAccess(Long userId, Set<Long> unitIds) {
	}
}
