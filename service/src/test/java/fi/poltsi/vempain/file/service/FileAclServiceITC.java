package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.entity.Unit;
import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.repository.files.ImageFileRepository;
import fi.poltsi.vempain.file.schedule.FileAclRepairSchedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for {@link FileAclService}: the readable-files specification, the require/can helpers and the daily ACL repair are
 * verified against real ACL rows in PostgreSQL.
 */
@SpringBootTest(properties = {
		"vempain.app.frontend-url=http://localhost:3000",
		"vempain.original-root-directory=/tmp",
		"vempain.export-root-directory=/tmp",
		"vempain.generate-missing-thumbnails.batch-size=10",
		"vempain.generate-missing-thumbnails.thumb-image-quality=0.5",
		"vempain.generate-missing-thumbnails.thumb-image-size=100"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FileAclServiceITC {
	private static final String FILE_PATH = "/itc/file-acl";
	private static final long   USER_ID   = 1L;   // seeded by the auth migration
	private static final long   OTHER_ID  = 2L;   // acl rows carry no foreign key to user_account

	private static final long OWN_FILE        = 97_001L;
	private static final long OTHER_USER_FILE = 97_002L;
	private static final long UNIT_FILE       = 97_003L;
	private static final long UNASSIGNED_FILE = 97_004L;
	private static final long ORPHAN_FILE     = 97_005L;
	private static final long NO_READ_FILE    = 97_006L;
	private static final long ORPHAN_ACL_ID   = 9_700_005L;

	@Autowired
	private FileAclService        fileAclService;
	@Autowired
	private FileAclRepairSchedule fileAclRepairSchedule;
	@Autowired
	private ImageFileRepository   imageFileRepository;
	@Autowired
	private FileRepository        fileRepository;
	@Autowired
	private JdbcTemplate          jdbcTemplate;

	private long unitId;

	@BeforeEach
	void seedData() {
		jdbcTemplate.update("INSERT INTO unit (acl_id, description, name, locked, creator, created) VALUES (9700000, 'ITC unit', 'file-acl-itc-unit', false, ?, NOW())",
							USER_ID);
		unitId = jdbcTemplate.queryForObject("SELECT id FROM unit WHERE name = 'file-acl-itc-unit'", Long.class);

		seedFile(OWN_FILE, OWN_FILE, "own.jpg");
		seedAcl(OWN_FILE, USER_ID, null, true, true, false);
		seedFile(OTHER_USER_FILE, OTHER_USER_FILE, "other.jpg");
		seedAcl(OTHER_USER_FILE, OTHER_ID, null, true, true, true);
		seedFile(UNIT_FILE, UNIT_FILE, "unit.jpg");
		seedAcl(UNIT_FILE, null, unitId, true, false, false);
		seedFile(UNASSIGNED_FILE, 0L, "unassigned.jpg");
		seedFile(ORPHAN_FILE, ORPHAN_ACL_ID, "orphan.jpg");
		seedFile(NO_READ_FILE, NO_READ_FILE, "no-read.jpg");
		seedAcl(NO_READ_FILE, USER_ID, null, false, true, true);
	}

	@AfterEach
	void cleanup() {
		SecurityContextHolder.clearContext();
		jdbcTemplate.update("DELETE FROM files WHERE file_path = ?", FILE_PATH);
		jdbcTemplate.update("DELETE FROM acl WHERE acl_id IN (?, ?, ?, ?, ?, ?, ?) OR unit_id = ?",
							OWN_FILE, OTHER_USER_FILE, UNIT_FILE, ORPHAN_ACL_ID, NO_READ_FILE, UNASSIGNED_FILE, 9_700_000L, unitId);
		jdbcTemplate.update("DELETE FROM acl WHERE acl_id > 9000000 AND acl_id NOT IN (SELECT acl_id FROM files)");
		jdbcTemplate.update("DELETE FROM unit WHERE id = ?", unitId);
	}

	@Test
	void readableFilesContainsOnlyFilesGrantedToUserOrUnit() {
		authenticate(USER_ID, Set.of(unit()));

		var visible = idsOf(imageFileRepository.findAll(fileAclService.readableFiles(pathSpecification())));

		assertThat(visible).containsExactlyInAnyOrder(OWN_FILE, UNIT_FILE);
	}

	@Test
	void unitFilesAreHiddenFromUsersOutsideTheUnit() {
		authenticate(USER_ID, Set.of());

		var visible = idsOf(imageFileRepository.findAll(fileAclService.readableFiles(pathSpecification())));

		assertThat(visible).containsExactly(OWN_FILE);
	}

	@Test
	void readableFilesWithoutSearchSpecificationStillFilters() {
		authenticate(USER_ID, Set.of());

		var visible = idsOf(imageFileRepository.findAll(fileAclService.readableFiles(null)));

		assertThat(visible).contains(OWN_FILE)
						   .doesNotContain(OTHER_USER_FILE, UNASSIGNED_FILE, ORPHAN_FILE, NO_READ_FILE, UNIT_FILE);
	}

	@Test
	void anonymousAndForeignPrincipalsSeeNothing() {
		SecurityContextHolder.clearContext();
		assertThat(imageFileRepository.findAll(fileAclService.readableFiles(pathSpecification()))).isEmpty();

		SecurityContextHolder.getContext()
							 .setAuthentication(new UsernamePasswordAuthenticationToken("plain-user", "password", List.of()));
		assertThat(imageFileRepository.findAll(fileAclService.readableFiles(pathSpecification()))).isEmpty();
	}

	@Test
	void requireHelpersEnforceTheRequestedPrivilege() {
		authenticate(USER_ID, Set.of(unit()));

		fileAclService.requireRead(file(OWN_FILE));
		fileAclService.requireModify(file(OWN_FILE));
		assertThatThrownBy(() -> fileAclService.requireDelete(file(OWN_FILE))).isInstanceOf(AccessDeniedException.class);

		fileAclService.requireRead(file(UNIT_FILE));
		assertThatThrownBy(() -> fileAclService.requireModify(file(UNIT_FILE))).isInstanceOf(AccessDeniedException.class);

		assertThatThrownBy(() -> fileAclService.requireRead(file(OTHER_USER_FILE))).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> fileAclService.requireRead(file(UNASSIGNED_FILE))).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> fileAclService.requireRead(file(ORPHAN_FILE))).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> fileAclService.requireRead(file(NO_READ_FILE))).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> fileAclService.requireRead(null)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void collectionModifyCheckRequiresEveryFile() {
		authenticate(USER_ID, Set.of());

		fileAclService.requireModify(List.of(file(OWN_FILE), file(NO_READ_FILE)));
		fileAclService.requireModify((List<FileEntity>) null);
		assertThat(fileAclService.canModifyAll(List.of())).isTrue();
		assertThat(fileAclService.canModifyAll(null)).isTrue();
		assertThat(fileAclService.canModifyAll(List.of(file(OWN_FILE), file(NO_READ_FILE)))).isTrue();
		assertThat(fileAclService.canModifyAll(List.of(file(OWN_FILE), file(OTHER_USER_FILE)))).isFalse();
		assertThatThrownBy(() -> fileAclService.requireModify(List.of(file(OWN_FILE), file(OTHER_USER_FILE))))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void canReadReflectsAclRows() {
		authenticate(USER_ID, Set.of());

		assertThat(fileAclService.canRead(file(OWN_FILE))).isTrue();
		assertThat(fileAclService.canRead(file(NO_READ_FILE))).isFalse();
		assertThat(fileAclService.canRead(file(OTHER_USER_FILE))).isFalse();
		assertThat(fileAclService.canRead(file(UNASSIGNED_FILE))).isFalse();
		assertThat(fileAclService.canRead(file(ORPHAN_FILE))).isFalse();
		assertThat(fileAclService.canRead(null)).isFalse();
		assertThat(fileAclService.canModify(file(NO_READ_FILE))).isTrue();
	}

	@Test
	void repairScheduleCreatesCreatorAclsForUnassignedAndOrphanedFiles() {
		authenticate(USER_ID, Set.of());
		assertThat(fileAclService.canRead(file(UNASSIGNED_FILE))).isFalse();
		assertThat(fileAclService.canRead(file(ORPHAN_FILE))).isFalse();

		var repaired = fileAclRepairSchedule.repairMissingAcls();

		assertThat(repaired).isEqualTo(2L);
		var unassigned = file(UNASSIGNED_FILE);
		var orphan     = file(ORPHAN_FILE);
		assertThat(unassigned.getAclId()).isPositive();
		assertThat(orphan.getAclId()).isPositive()
									 .isNotEqualTo(ORPHAN_ACL_ID);
		// the creator of the seeded files is user 1, so the repaired files become fully accessible to that user
		assertThat(fileAclService.canRead(unassigned)).isTrue();
		assertThat(fileAclService.canModify(orphan)).isTrue();
		assertThat(fileRepository.findFilesWithoutAcl()).noneMatch(entity -> FILE_PATH.equals(entity.getFilePath()));
		assertThat(fileAclRepairSchedule.repairMissingAcls()).isZero();
	}

	private Specification<ImageFileEntity> pathSpecification() {
		return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("filePath"), FILE_PATH);
	}

	private List<Long> idsOf(List<ImageFileEntity> files) {
		return files.stream()
					.map(FileEntity::getId)
					.toList();
	}

	private FileEntity file(long id) {
		return fileRepository.findById(id)
							 .orElseThrow();
	}

	private Unit unit() {
		return Unit.builder()
				   .id(unitId)
				   .build();
	}

	private void authenticate(long userId, Set<Unit> units) {
		var principal = new UserDetailsImpl(userId, "admin", "Admin", "admin@nohost.nodomain", "password", units, List.of());
		SecurityContextHolder.getContext()
							 .setAuthentication(new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities()));
	}

	private void seedFile(long id, long aclId, String filename) {
		jdbcTemplate.update(
				"""
						INSERT INTO files
						    (id, acl_id, external_file_id, filename, file_path, mimetype,
						     filesize, sha256sum, file_type, creator, created, locked, metadata_raw)
						OVERRIDING SYSTEM VALUE
						VALUES (?, ?, ?, ?, ?, 'image/jpeg', 1024,
						        'a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0',
						        'IMAGE', ?, NOW(), false, '{}')
						""",
				id, aclId, "file-acl-itc-" + id, filename, FILE_PATH, USER_ID);
		jdbcTemplate.update("INSERT INTO image_files (id, width, height, color_depth, dpi, group_label) VALUES (?, 640, 480, 24, 72, 'file-acl')", id);
	}

	private void seedAcl(long aclId, Long userId, Long unitId, boolean read, boolean modify, boolean delete) {
		jdbcTemplate.update(
				"INSERT INTO acl (acl_id, user_id, unit_id, create_privilege, read_privilege, modify_privilege, delete_privilege) VALUES (?, ?, ?, true, ?, ?, ?)",
				aclId, userId, unitId, read, modify, delete);
	}
}
