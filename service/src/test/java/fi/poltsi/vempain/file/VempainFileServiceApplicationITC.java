package fi.poltsi.vempain.file;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@EnableAutoConfiguration
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"vempain.app.frontend-url=http://localhost:3000",
		"vempain.original-root-directory=/tmp",
		"vempain.export-root-directory=/tmp"
})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VempainFileServiceApplicationITC {
	@Container
	public static PostgreSQLContainer vempainAdminContainer = new PostgreSQLContainer("postgres:18-alpine")
			.withDatabaseName("vempain_file_db")
			.withUsername("test")
			.withPassword("test");

	@Test
	void contextLoads() {
	}
}
