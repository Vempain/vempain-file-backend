package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.entity.Acl;
import fi.poltsi.vempain.auth.entity.Unit;
import fi.poltsi.vempain.auth.repository.AclRepository;
import fi.poltsi.vempain.auth.security.AclAuthorizationService;
import fi.poltsi.vempain.file.entity.FileEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileAclService {
	private final AclRepository            aclRepository;
	private final AclAuthorizationService aclAuthorizationService;

	public <T extends FileEntity> Specification<T> readableFiles() {
		var access = currentAclAccess();
		return (root, query, criteriaBuilder) ->
		{
			var declaredAclIds = query.subquery(Long.class);
			var declaredAclRoot = declaredAclIds.from(Acl.class);
			declaredAclIds.select(declaredAclRoot.get("aclId"));
			declaredAclIds.where(criteriaBuilder.equal(declaredAclRoot.get("aclId"), root.get("aclId")));

			var accessibleAclIds  = query.subquery(Long.class);
			var accessibleAclRoot = accessibleAclIds.from(Acl.class);
			accessibleAclIds.select(accessibleAclRoot.get("aclId"));
			accessibleAclIds.where(
					criteriaBuilder.equal(accessibleAclRoot.get("aclId"), root.get("aclId")),
					criteriaBuilder.isTrue(accessibleAclRoot.get("readPrivilege")),
					accessiblePrincipalPredicate(accessibleAclRoot, access, criteriaBuilder)
			);

			return criteriaBuilder.or(
					criteriaBuilder.lessThanOrEqualTo(root.get("aclId"), 0L),
					criteriaBuilder.not(criteriaBuilder.exists(declaredAclIds)),
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

	public void requireDelete(FileEntity file) {
		require(file, AclAuthorizationService.AclPrivilege.DELETE);
	}

	public boolean canRead(FileEntity file) {
		return file != null
				&& (!isProtected(file.getAclId()) || aclAuthorizationService.canRead(file.getAclId()));
	}

	private void require(FileEntity file, AclAuthorizationService.AclPrivilege privilege) {
		if (file == null || (isProtected(file.getAclId()) && !aclAuthorizationService.hasPrivilege(
				file.getAclId(), privilege, SecurityContextHolder.getContext().getAuthentication()))) {
			throw new AccessDeniedException("The current user has no " + privilege.name().toLowerCase() + " access to the file");
		}
	}

	private boolean isProtected(long aclId) {
		return aclId > 0 && !aclRepository.getAclByAclId(aclId).isEmpty();
	}

	private Predicate accessiblePrincipalPredicate(Root<Acl> aclRoot,
												   AclAccess access,
												   CriteriaBuilder criteriaBuilder) {
		var predicates = new java.util.ArrayList<Predicate>();
		if (access.userId() != null) {
			predicates.add(criteriaBuilder.equal(aclRoot.get("userId"), access.userId()));
		}
		if (!access.unitIds()
		           .isEmpty()) {
			predicates.add(criteriaBuilder.and(
					criteriaBuilder.isNull(aclRoot.get("userId")),
					aclRoot.get("unitId")
					       .in(access.unitIds())
			));
		}
		return predicates.isEmpty()
			   ? criteriaBuilder.disjunction()
			   : criteriaBuilder.or(predicates.toArray(Predicate[]::new));
	}

	private AclAccess currentAclAccess() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof fi.poltsi.vempain.auth.service.UserDetailsImpl user)) {
			return new AclAccess(null, Set.of());
		}

		var unitIds = user.getUnits() == null
				? Set.<Long>of()
				: user.getUnits().stream().map(Unit::getId).filter(Objects::nonNull).collect(Collectors.toSet());
		return new AclAccess(user.getId(), unitIds);
	}

	private record AclAccess(Long userId, Set<Long> unitIds) {
	}
}
