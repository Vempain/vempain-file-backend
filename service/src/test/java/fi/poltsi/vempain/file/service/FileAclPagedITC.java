package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import fi.poltsi.vempain.file.service.files.ImageFileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"vempain.app.frontend-url=http://localhost:3000",
		"vempain.original-root-directory=/tmp",
		"vempain.export-root-directory=/tmp",
		"vempain.generate-missing-thumbnails.batch-size=10",
		"vempain.generate-missing-thumbnails.thumb-image-quality=0.5",
		"vempain.generate-missing-thumbnails.thumb-image-size=100"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FileAclPagedITC {

	private static final long FILE_ID        = 98_001L;
	private static final long FILE_ACL_ID    = 9_000_000L;
	private static final long BULK_ACL_START = 10_000_000L;
	private static final int  BULK_ACL_COUNT = 66_000;

	@Autowired
	private ImageFileService imageFileService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void seedData() {
		jdbcTemplate.update(
				"""
						INSERT INTO files
						    (id, acl_id, external_file_id, filename, file_path, mimetype,
						     filesize, sha256sum, file_type, creator, created, locked, metadata_raw)
						OVERRIDING SYSTEM VALUE
						VALUES (?, ?, ?, 'acl-scale.jpg', '/itc/acl-scale', 'image/jpeg', 1024, left(
						        'a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0',
						        64), 'IMAGE', 1, NOW(), false, '{}')
						""",
				FILE_ID, FILE_ACL_ID, "acl-scale-" + FILE_ID);
		jdbcTemplate.update(
				"INSERT INTO image_files (id, width, height, color_depth, dpi, group_label) VALUES (?, 1920, 1080, 24, 72, 'acl-scale')",
				FILE_ID);
		jdbcTemplate.update(
				"INSERT INTO acl (acl_id, user_id, unit_id, create_privilege, read_privilege, modify_privilege, delete_privilege) VALUES (?, 1, null, true, true, true, true)",
				FILE_ACL_ID);
		jdbcTemplate.batchUpdate(
				"INSERT INTO acl (acl_id, user_id, unit_id, create_privilege, read_privilege, modify_privilege, delete_privilege) VALUES (?, 1, null, true, true, true, true)",
				new BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement statement, int index) throws SQLException {
						statement.setLong(1, BULK_ACL_START + index);
					}

					@Override
					public int getBatchSize() {
						return BULK_ACL_COUNT;
					}
				});

		var principal = new UserDetailsImpl(1L, "admin", "Admin", "admin@nohost.nodomain", "password", Set.of(), List.of());
		SecurityContextHolder.getContext()
		                     .setAuthentication(
									 new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities()));
	}

	@AfterEach
	void cleanup() {
		SecurityContextHolder.clearContext();
		jdbcTemplate.update("DELETE FROM files WHERE id = ?", FILE_ID);
		jdbcTemplate.update("DELETE FROM acl WHERE acl_id = ? OR acl_id BETWEEN ? AND ?",
							FILE_ACL_ID, BULK_ACL_START, BULK_ACL_START + BULK_ACL_COUNT - 1);
	}

	@Test
	void pagedQuery_handlesMoreAclRowsThanPostgresParameterLimit() {
		var response = imageFileService.findAll(new fi.poltsi.vempain.auth.api.request.PagedRequest(0, 10, "filename", null, null, false));

		assertThat(response.getTotalElements()).isEqualTo(1);
		assertThat(response.getContent()).hasSize(1);
		assertThat(response.getContent()
		                   .getFirst()
		                   .getId()).isEqualTo(FILE_ID);
	}
}
