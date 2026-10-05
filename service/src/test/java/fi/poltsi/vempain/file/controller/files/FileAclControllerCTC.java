package fi.poltsi.vempain.file.controller.files;

import fi.poltsi.vempain.file.controller.AbstractControllerCTC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies through the REST layer that file access is decided by the ACL rows of the file: a file readable by the caller is returned,
 * a file granted to another user, a file without ACL rows and a file without the requested privilege are rejected.
 */
class FileAclControllerCTC extends AbstractControllerCTC {
	private static final long OWN_FILE        = 9101L;
	private static final long OTHER_USER_FILE = 9102L;
	private static final long NO_ACL_FILE     = 9103L;
	private static final long READ_ONLY_FILE  = 9104L;

	@BeforeEach
	void seed() {
		seedFileRowWithAcl(OWN_FILE, "ARCHIVE", "application/zip", "own.zip", "/acl-ctc", 1L, true, true, true);
		seedFileRowWithAcl(OTHER_USER_FILE, "ARCHIVE", "application/zip", "other.zip", "/acl-ctc", 2L, true, true, true);
		seedFileRowWithAcl(NO_ACL_FILE, "ARCHIVE", "application/zip", "orphan.zip", "/acl-ctc", null, true, true, true);
		seedFileRowWithAcl(READ_ONLY_FILE, "ARCHIVE", "application/zip", "read-only.zip", "/acl-ctc", 1L, true, false, false);
		for (var id : new long[]{OWN_FILE, OTHER_USER_FILE, NO_ACL_FILE, READ_ONLY_FILE}) {
			jdbcTemplate.update("INSERT INTO archive_files (id, compression_method, uncompressed_size, content_count, is_encrypted) VALUES (?, 'zip', 2048, 2, false)", id);
		}
	}

	@AfterEach
	void cleanup() {
		for (var id : new long[]{OWN_FILE, OTHER_USER_FILE, NO_ACL_FILE, READ_ONLY_FILE}) {
			deleteFileRow(id);
		}
	}

	@Test
	void pagedListingContainsOnlyReadableFiles() throws Exception {
		doPost("/files/archive/paged", "{\"page\":0,\"size\":50,\"search\":\"/acl-ctc\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].id").value(hasItem((int) OWN_FILE)))
				.andExpect(jsonPath("$.content[*].id").value(hasItem((int) READ_ONLY_FILE)))
				.andExpect(jsonPath("$.content[*].id").value(not(hasItem((int) OTHER_USER_FILE))))
				.andExpect(jsonPath("$.content[*].id").value(not(hasItem((int) NO_ACL_FILE))));
	}

	@Test
	void readableFileIsReturned() throws Exception {
		doGet("/files/archive/" + OWN_FILE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(OWN_FILE));
	}

	@Test
	void fileGrantedToAnotherUserIsForbidden() throws Exception {
		doGet("/files/archive/" + OTHER_USER_FILE)
				.andExpect(status().isForbidden());
	}

	@Test
	void fileWithoutAclRowsIsForbidden() throws Exception {
		doGet("/files/archive/" + NO_ACL_FILE)
				.andExpect(status().isForbidden());
		doDelete("/files/archive/" + NO_ACL_FILE)
				.andExpect(status().isForbidden());
	}

	@Test
	void deleteRequiresDeletePrivilege() throws Exception {
		doDelete("/files/archive/" + READ_ONLY_FILE)
				.andExpect(status().isForbidden());
		doDelete("/files/archive/" + OWN_FILE)
				.andExpect(status().isOk());
	}

	@Test
	void contentDownloadRequiresReadPrivilege() throws Exception {
		doGet("/files/" + OTHER_USER_FILE + "/content")
				.andExpect(status().isForbidden());
	}
}
