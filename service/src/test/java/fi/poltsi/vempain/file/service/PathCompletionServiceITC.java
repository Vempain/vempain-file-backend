package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.PathCompletionEnum;
import fi.poltsi.vempain.file.api.request.PathCompletionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests (ITC) for {@link PathCompletionService}.
 *
 * <p>Uses a temp directory to exercise the filesystem-based path completion logic without a full
 * Spring context.</p>
 */
class PathCompletionServiceITC {

    private final PathCompletionService service = new PathCompletionService();

    @TempDir
    Path originalRoot;

    @TempDir
    Path exportRoot;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "originalRootDirectory", originalRoot.toString());
        ReflectionTestUtils.setField(service, "exportedRootDirectory", exportRoot.toString());
    }

    @Test
    @DisplayName("completePath with root '/' lists immediate subdirectories")
    void completePath_root_listsSubDirs() throws IOException {
        Files.createDirectories(originalRoot.resolve("photos"));
        Files.createDirectories(originalRoot.resolve("documents"));

        var req = new PathCompletionRequest("/", PathCompletionEnum.ORIGINAL);
        var response = service.completePath(req);

        assertThat(response.getCompletions()).hasSize(2);
        assertThat(response.getCompletions()).anyMatch(p -> p.endsWith("photos"));
        assertThat(response.getCompletions()).anyMatch(p -> p.endsWith("documents"));
    }

    @Test
    @DisplayName("completePath with exact existing directory lists its children")
    void completePath_exactDir_listsChildren() throws IOException {
        var sub = originalRoot.resolve("events");
        Files.createDirectories(sub.resolve("2023"));
        Files.createDirectories(sub.resolve("2024"));

        var req = new PathCompletionRequest("/events", PathCompletionEnum.ORIGINAL);
        var response = service.completePath(req);

        assertThat(response.getCompletions()).anyMatch(p -> p.endsWith("2023") || p.endsWith("2024"));
    }

    @Test
    @DisplayName("completePath with prefix does prefix matching in parent dir")
    void completePath_prefix_returnsMatchingDirs() throws IOException {
        Files.createDirectories(originalRoot.resolve("summer2023"));
        Files.createDirectories(originalRoot.resolve("summer2024"));
        Files.createDirectories(originalRoot.resolve("winter2023"));

        var req = new PathCompletionRequest("/summer", PathCompletionEnum.ORIGINAL);
        var response = service.completePath(req);

        assertThat(response.getCompletions()).hasSize(2);
        assertThat(response.getCompletions()).allMatch(p -> p.contains("summer"));
    }

    @Test
    @DisplayName("completePath with null path treats as root")
    void completePath_nullPath_treatsAsRoot() throws IOException {
        Files.createDirectories(originalRoot.resolve("album"));

        var req = new PathCompletionRequest(null, PathCompletionEnum.ORIGINAL);
        var response = service.completePath(req);

        assertThat(response.getCompletions()).anyMatch(p -> p.endsWith("album"));
    }

    @Test
    @DisplayName("completePath with empty path treats as root")
    void completePath_emptyPath_treatsAsRoot() throws IOException {
        Files.createDirectories(originalRoot.resolve("gallery"));

        var req = new PathCompletionRequest("", PathCompletionEnum.ORIGINAL);
        var response = service.completePath(req);

        assertThat(response.getCompletions()).anyMatch(p -> p.endsWith("gallery"));
    }

    @Test
    @DisplayName("completePath with path traversal attempt returns empty")
    void completePath_pathTraversal_returnsEmpty() {
        var req = new PathCompletionRequest("/../../../etc", PathCompletionEnum.ORIGINAL);
        var response = service.completePath(req);

        assertThat(response.getCompletions()).isEmpty();
    }

    @Test
    @DisplayName("completePath with EXPORTED type uses export root")
    void completePath_exportType_usesExportRoot() throws IOException {
        Files.createDirectories(exportRoot.resolve("exports"));

        var req = new PathCompletionRequest("/", PathCompletionEnum.EXPORTED);
        var response = service.completePath(req);

        assertThat(response.getCompletions()).anyMatch(p -> p.endsWith("exports"));
    }

    @Test
    @DisplayName("completePath hides hidden directories (starting with .)")
    void completePath_hidesHiddenDirs() throws IOException {
        Files.createDirectories(originalRoot.resolve("visible"));
        Files.createDirectories(originalRoot.resolve(".hidden"));

        var req = new PathCompletionRequest("/", PathCompletionEnum.ORIGINAL);
        var response = service.completePath(req);

        assertThat(response.getCompletions()).noneMatch(p -> p.contains(".hidden"));
        assertThat(response.getCompletions()).anyMatch(p -> p.endsWith("visible"));
    }

    @Test
    @DisplayName("completePath with files in directory ignores non-directories")
    void completePath_ignoresFiles() throws IOException {
        Files.createDirectories(originalRoot.resolve("subdir"));
        Files.writeString(originalRoot.resolve("file.txt"), "content");

        var req = new PathCompletionRequest("/", PathCompletionEnum.ORIGINAL);
        var response = service.completePath(req);

        assertThat(response.getCompletions()).noneMatch(p -> p.endsWith("file.txt"));
    }
}
