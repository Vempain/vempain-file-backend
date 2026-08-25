package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.PathCompletionEnum;
import fi.poltsi.vempain.file.api.request.PathCompletionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PathCompletionServiceUTC {

	@Test
	void listsSubDirectories(@TempDir Path root) throws Exception {
		Files.createDirectories(root.resolve("album"));
		var service = new PathCompletionService();
		ReflectionTestUtils.setField(service, "originalRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "exportedRootDirectory", root.toString());

		var response = service.completePath(new PathCompletionRequest("/", PathCompletionEnum.ORIGINAL));

		assertThat(response.getCompletions()).contains("/album");
	}
}
