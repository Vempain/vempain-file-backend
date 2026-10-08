package fi.poltsi.vempain.file;

import fi.poltsi.vempain.common.api.TaskStatusEnum;
import fi.poltsi.vempain.common.task.TaskProgressStore;
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
		"vempain.export-root-directory=/tmp",
		"vempain.generate-missing-thumbnails.batch-size=10",
		"vempain.generate-missing-thumbnails.thumb-image-quality=0.5",
		"vempain.generate-missing-thumbnails.thumb-image-size=100"
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
	private TaskProgressStore taskProgressStore;

	@BeforeEach
	void setup() {
		// Ensure table exists; simple schema sufficient for this integration test
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS file_group (id BIGSERIAL PRIMARY KEY, path VARCHAR(255), group_name VARCHAR(255));");
		// Clean tables that will be used in the test.
		jdbcTemplate.update("TRUNCATE TABLE file_group RESTART IDENTITY CASCADE");
	}

	@Test
	void publishAllRunsAsOneTaskWithAStepPerGroup_andCompletes() throws InterruptedException {
		jdbcTemplate.update("INSERT INTO file_group (path, group_name) VALUES (?, ?)", "/g1", "group1");
		jdbcTemplate.update("INSERT INTO file_group (path, group_name) VALUES (?, ?)", "/g2", "group2");
		jdbcTemplate.update("INSERT INTO file_group (path, group_name) VALUES (?, ?)", "/g3", "group3");

		var task = publishService.publishAllFileGroups();

		assertEquals(3L, task.getTotalSteps()
							 .get(), "publishAllFileGroups should schedule three groups");
		assertTrue(taskProgressStore.find(task.getId())
									.isPresent(), "the task must be registered for polling");

		// Wait for the background task to finish
		Instant deadline = Instant.now()
								  .plus(Duration.ofSeconds(10));
		while (!task.isFinished() && Instant.now()
											.isBefore(deadline)) {
			Thread.sleep(100);
		}

		assertEquals(TaskStatusEnum.COMPLETED, task.getStatus(), "The publish-all task should complete");
		assertEquals(3L, task.getCompletedSteps()
							 .get(), "All groups should have been processed");
		assertEquals(0L, task.getFailedSteps()
							 .get(), "Empty groups publish without failures");
		assertEquals(100, task.toResponse()
							  .getPercent());
		taskProgressStore.remove(task.getId());
	}
}
