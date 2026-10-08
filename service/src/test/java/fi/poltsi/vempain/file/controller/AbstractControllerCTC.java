package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Abstract base class for Controller Test Classes (CTC).
 *
 * <p>All concrete CTCs should extend this class. Provides:
 * <ul>
 *   <li>Spring Boot full-context test setup with MockMvc</li>
 *   <li>Testcontainers PostgreSQL via {@code jdbc:tc:} URL in test/resources/application.yaml</li>
 *   <li>Authenticated helper methods for GET / POST / PUT / DELETE</li>
 *   <li>A {@link JdbcTemplate} for data seeding and cleanup</li>
 * </ul>
 */
@SpringBootTest(properties = {
		"vempain.app.frontend-url=http://localhost:3000",
		"vempain.original-root-directory=/tmp",
		"vempain.export-root-directory=/tmp",
		"vempain.generate-missing-thumbnails.batch-size=10",
		"vempain.generate-missing-thumbnails.thumb-image-quality=0.5",
		"vempain.generate-missing-thumbnails.thumb-image-size=100",
		// Controller tests create task rows directly and drive their state in memory; the periodic worker poll must not claim
		// those QUEUED rows. Submitted tasks still run, because TaskRunner polls once after the submitting transaction commits.
		"vempain.tasks.poll-interval-ms=3600000",
		"vempain.tasks.heartbeat-interval-ms=3600000",
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractControllerCTC {

	/**
	 * Flyway seeds user_account id=1 ("admin"). Requests run as that user so that ACL rows created by {@link #seedFileRow} grant access.
	 */
	protected static final UserDetailsImpl CTC_PRINCIPAL =
			new UserDetailsImpl(1L, "admin", "Admin", "admin@nohost.nodomain", "Disabled", Set.of(), List.of());

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	// -----------------------------------------------------------------------
	// Helper methods — authenticated requests
	// -----------------------------------------------------------------------

	protected ResultActions doGet(String path) throws Exception {
		return mockMvc.perform(
				get(path).with(user(CTC_PRINCIPAL)));
	}

	protected ResultActions doPost(String path, String body) throws Exception {
		return mockMvc.perform(
				post(path)
						.with(user(CTC_PRINCIPAL))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body));
	}

	protected ResultActions doPut(String path, String body) throws Exception {
		return mockMvc.perform(
				put(path)
						.with(user(CTC_PRINCIPAL))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body));
	}

	protected ResultActions doDelete(String path) throws Exception {
		return mockMvc.perform(
				delete(path)
						.with(user(CTC_PRINCIPAL))
						.with(csrf()));
	}

	// -----------------------------------------------------------------------
	// Seed helpers
	// -----------------------------------------------------------------------

	/**
	 * Inserts a row into {@code files} (parent table, JOINED inheritance) using
	 * {@code OVERRIDING SYSTEM VALUE} so that an explicit ID can be provided, together with an ACL row ({@code acl_id = id}) that grants
	 * every privilege to user 1, the principal used by the authenticated request helpers.
	 *
	 * @param id       explicit file id (use values ≥ 9001 to avoid Flyway-seed conflicts)
	 * @param fileType file_type column value, e.g. "ARCHIVE"
	 * @param mimeType MIME type string
	 * @param filename file name
	 * @param filePath directory path
	 */
	protected void seedFileRow(long id, String fileType, String mimeType, String filename, String filePath) {
		seedFileRowWithAcl(id, fileType, mimeType, filename, filePath, 1L, true, true, true);
	}

	/**
	 * Inserts a file row whose ACL ({@code acl_id = id}) grants the given privileges to {@code aclUserId}. Pass {@code null} as the user
	 * to seed a file without any ACL rows.
	 */
	protected void seedFileRowWithAcl(long id, String fileType, String mimeType, String filename, String filePath,
									  Long aclUserId, boolean read, boolean modify, boolean delete) {
		jdbcTemplate.update(
				"""
						INSERT INTO files
						    (id, acl_id, external_file_id, filename, file_path, mimetype,
						     filesize, sha256sum, file_type, creator, created, locked, metadata_raw)
						OVERRIDING SYSTEM VALUE
						VALUES (?, ?, ?, ?, ?, ?, 1024,
						        'a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0',
						        ?, 1, NOW(), false, '{}')
						""",
				id, id, "test-ext-" + fileType.toLowerCase(),
				filename, filePath, mimeType, fileType);

		if (aclUserId != null) {
			jdbcTemplate.update(
					"INSERT INTO acl (acl_id, user_id, unit_id, create_privilege, read_privilege, modify_privilege, delete_privilege) VALUES (?, ?, null, true, ?, ?, ?)",
					id, aclUserId, read, modify, delete);
		}
	}

	/**
	 * Deletes a file row (and cascades to the type-specific child table).
	 */
	protected void deleteFileRow(long id) {
		jdbcTemplate.update("DELETE FROM files WHERE id = ?", id);
		jdbcTemplate.update("DELETE FROM acl WHERE acl_id = ?", id);
	}
}
