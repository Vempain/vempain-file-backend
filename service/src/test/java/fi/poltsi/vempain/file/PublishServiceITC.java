package fi.poltsi.vempain.file;

import fi.poltsi.vempain.file.api.PublishProgressStatusEnum;
import fi.poltsi.vempain.file.service.PublishProgressStore;
import fi.poltsi.vempain.file.service.PublishService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
		"vempain.app.frontend-url=http://localhost:3000",
		"vempain.original-root-directory=/tmp",
		"vempain.export-root-directory=/tmp"
})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PublishServiceITC {
	@Container
	public static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
			.withDatabaseName("vempain_file_db")
			.withUsername("test")
			.withPassword("test");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PublishService publishService;

	@Autowired
	private PublishProgressStore progressStore;

	@BeforeEach
	void setup() {
		// Ensure table exists; simple schema sufficient for this integration test
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS file_group (id BIGSERIAL PRIMARY KEY, path VARCHAR(255), group_name VARCHAR(255));");
		// Clean tables that will be used in the test.
		jdbcTemplate.update("TRUNCATE TABLE file_group RESTART IDENTITY CASCADE");
	}

	@Test
	void publishAllSchedulesAllGroups_andCompletes() throws InterruptedException {
		// Insert 3 file_group rows (only columns that are commonly present)
		jdbcTemplate.update("INSERT INTO file_group (path, group_name) VALUES (?, ?)",
							"/g1", "group1");
		jdbcTemplate.update("INSERT INTO file_group (path, group_name) VALUES (?, ?)",
							"/g2", "group2");
		jdbcTemplate.update("INSERT INTO file_group (path, group_name) VALUES (?, ?)",
							"/g3", "group3");

		long scheduled = publishService.publishAllFileGroups();

		assertEquals(3L, scheduled, "publishAllFileGroups should schedule three groups");
		// progress store should reflect scheduled and total count immediately
		assertEquals(3L, progressStore.getScheduled());
		assertEquals(3L, progressStore.getTotal());

		// Wait for async processing to mark them started/completed
		Instant deadline = Instant.now()
								  .plus(Duration.ofSeconds(5));
		while (Instant.now()
					  .isBefore(deadline)) {
			if (progressStore.getCompleted() >= 3L && progressStore.getStarted() >= 3L) {
				break;
			}
			Thread.sleep(100);
		}

		assertEquals(3L, progressStore.getStarted(), "All groups should have been started");
		assertEquals(3L, progressStore.getCompleted(), "All groups should have completed");

		// Ensure per-group statuses are set to COMPLETED
		assertTrue(progressStore.getPerGroupStatus()
								.values()
								.stream()
								.allMatch(s -> s == PublishProgressStatusEnum.COMPLETED));
	}
}
