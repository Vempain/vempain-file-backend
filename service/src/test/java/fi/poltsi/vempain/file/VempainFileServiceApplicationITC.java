package fi.poltsi.vempain.file;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"vempain.app.frontend-url=http://localhost:3000",
		"vempain.original-root-directory=/tmp",
		"vempain.export-root-directory=/tmp"
})
class VempainFileServiceApplicationITC {
	@Test
	void contextLoads() {
	}

}
