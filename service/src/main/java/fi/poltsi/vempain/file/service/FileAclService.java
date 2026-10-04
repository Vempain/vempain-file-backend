package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.entity.Acl;
import fi.poltsi.vempain.auth.entity.Unit;
import fi.poltsi.vempain.auth.repository.AclRepository;
import fi.poltsi.vempain.auth.security.AclAuthorizationService;
import fi.poltsi.vempain.file.entity.FileEntity;
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
		var aclIds = accessibleAclIds(AclAuthorizationService.AclPrivilege.READ);
		return (root, query, criteriaBuilder) ->
		{
			var declaredAclIds = query.subquery(Long.class);
			var aclRoot = declaredAclIds.from(Acl.class);
			declaredAclIds.select(aclRoot.get("aclId"));
			return criteriaBuilder.or(criteriaBuilder.lessThanOrEqualTo(root.get("aclId"), 0L),
			                          criteriaBuilder.not(root.get("aclId").in(declaredAclIds)),
			                          aclIds.isEmpty() ? criteriaBuilder.disjunction() : root.get("aclId").in(aclIds));
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

	private Set<Long> accessibleAclIds(AclAuthorizationService.AclPrivilege privilege) {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof fi.poltsi.vempain.auth.service.UserDetailsImpl user)) {
			return Set.of();
		}

		var unitIds = user.getUnits() == null
				? Set.<Long>of()
				: user.getUnits().stream().map(Unit::getId).filter(Objects::nonNull).collect(Collectors.toSet());
		return aclRepository.findAll().stream()
		                    .filter(acl -> Objects.equals(acl.getUserId(), user.getId())
				                    || (acl.getUserId() == null && acl.getUnitId() != null && unitIds.contains(acl.getUnitId())))
		                    .filter(acl -> hasPrivilege(acl, privilege))
		                    .map(Acl::getAclId)
		                    .collect(Collectors.toSet());
	}

	private boolean hasPrivilege(Acl acl, AclAuthorizationService.AclPrivilege privilege) {
		return switch (privilege) {
			case READ -> acl.isReadPrivilege();
			case CREATE -> acl.isCreatePrivilege();
			case MODIFY -> acl.isModifyPrivilege();
			case DELETE -> acl.isDeletePrivilege();
		};
	}
}
