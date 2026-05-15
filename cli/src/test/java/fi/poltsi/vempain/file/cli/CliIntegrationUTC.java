package fi.poltsi.vempain.file.cli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jline.reader.Candidate;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliIntegrationUTC {

	@TempDir
	Path tempHome;

	private String previousHome;

	@BeforeEach
	void setUp() {
		previousHome = System.getProperty("user.home");
		System.setProperty("user.home", tempHome.toString());
	}

	@AfterEach
	void tearDown() {
		System.setProperty("user.home", previousHome);
	}

	@Test
	void loginAndFilesList_formatsOutputTable() throws Exception {
		try (var server = new MockApiServer()) {
			server.registerJson("/api/login", "[{\"token\":\"jwt-1\"}]");
			server.registerJson("/api/files/music/paged",
			                    """
										{
										  "content": [
										    {
										      "id": 42,
										      "filename": "this-is-a-very-long-file-name-that-needs-truncation.mp3",
										      "file_type": "MUSIC",
										      "filesize": 123456,
										      "created": "2026-05-14T12:00:00Z"
										    }
										  ],
										  "page": 0,
										  "total_pages": 1,
										  "total_elements": 1
										}
										""");

			var outBytes = new ByteArrayOutputStream();
			var errBytes = new ByteArrayOutputStream();
			var app      = new VempainFileCliApplication(new SessionStore(), new BackendClient(), new PrintStream(outBytes), new PrintStream(errBytes));

			app.run(new String[]{"login", "--url", server.baseUrl(), "--username", "admin", "--password", "secret"});
			app.run(new String[]{"files-list", "--type", "music", "--size", "10"});

			var output = outBytes.toString(StandardCharsets.UTF_8);
			assertTrue(output.contains("Login successful"));
			assertTrue(output.contains("id       filename"));
			assertTrue(output.contains("Page 0/1, total elements: 1"));
			assertTrue(output.contains("..."));
			assertFalse(errBytes.toString(StandardCharsets.UTF_8)
			                    .contains("Error:"));
		}
	}

	@Test
	void fileShow_supportsContentLimitAndRaw() throws Exception {
		try (var server = new MockApiServer()) {
			server.registerJson("/api/files/music/42",
			                    """
										{
										  "id": 42,
										  "filename": "song.txt",
										  "file_type": "MUSIC",
										  "mimetype": "text/plain",
										  "filesize": 20,
										  "created": "2026-05-14T12:00:00Z"
										}
										""");
			server.registerBytes("/api/files/42/content", "text/plain", "0123456789abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8));

			var sessionStore = new SessionStore();
			sessionStore.save(server.baseUrl(), "jwt-2");

			var outBytes = new ByteArrayOutputStream();
			var app      = new VempainFileCliApplication(sessionStore, new BackendClient(), new PrintStream(outBytes), System.err);

			app.run(new String[]{"file-show", "--type", "music", "--id", "42", "--content-limit", "10"});
			var truncatedOutput = outBytes.toString(StandardCharsets.UTF_8);
			assertTrue(truncatedOutput.contains("Metadata:"));
			assertTrue(truncatedOutput.contains("Content (truncated to 10 chars):"));

			outBytes.reset();
			app.run(new String[]{"file-show", "--type", "music", "--id", "42", "--raw", "--content-limit", "10"});
			var rawOutput = outBytes.toString(StandardCharsets.UTF_8);
			assertTrue(rawOutput.contains("\"filename\": \"song.txt\""));
			assertFalse(rawOutput.contains("Content (truncated to 10 chars):"));
			assertTrue(rawOutput.contains("abcdefghijklmnopqrstuvwxyz"));
		}
	}

	@Test
	void shellCompletion_completesFileTypeAndPath() throws Exception {
		try (var server = new MockApiServer()) {
			server.registerJson("/api/path-completion", "{\"completions\":[\"/music\",\"/museum\"]}");

			var sessionStore = new SessionStore();
			sessionStore.save(server.baseUrl(), "jwt-3");

			var app       = new VempainFileCliApplication(sessionStore, new BackendClient(), System.out, System.err);
			var completer = app.createCompleterForTests();

			var typeCandidates = new ArrayList<Candidate>();
			completer.complete(null, new StubParsedLine(List.of("file-show", "--type", "m"), 2, "m"), typeCandidates);
			assertTrue(typeCandidates.stream()
			                         .anyMatch(c -> "music".equals(c.value())));

			var pathCandidates = new ArrayList<Candidate>();
			completer.complete(null, new StubParsedLine(List.of("scan", "--original-directory", "/mu"), 2, "/mu"), pathCandidates);
			assertTrue(pathCandidates.stream()
			                         .anyMatch(c -> "/music".equals(c.value())));
		}
	}

	@Test
	void publishScanAndLogout_workEndToEnd() throws Exception {
		try (var server = new MockApiServer()) {
			server.registerJson("/api/data-publish/music", "{\"identifier\":\"music_library\",\"status\":\"ok\"}");
			server.registerJson("/api/data-publish/gps-timeseries", "{\"identifier\":\"gps_timeseries_demo\",\"status\":\"ok\"}");
			server.registerJson("/api/scan-files", "[{\"success\":true,\"newFilesCount\":1}]");

			var sessionStore = new SessionStore();
			sessionStore.save(server.baseUrl(), "jwt-4");

			var outBytes = new ByteArrayOutputStream();
			var errBytes = new ByteArrayOutputStream();
			var app      = new VempainFileCliApplication(sessionStore, new BackendClient(), new PrintStream(outBytes), new PrintStream(errBytes));

			app.run(new String[]{"publish-music"});
			app.run(new String[]{"publish-gps", "--file-group-id", "10", "--time-series-name", "demo"});
			app.run(new String[]{"scan", "--original-directory", "/music"});
			app.run(new String[]{"logout"});

			var output = outBytes.toString(StandardCharsets.UTF_8);
			assertTrue(output.contains("music_library"));
			assertTrue(output.contains("gps_timeseries_demo"));
			assertTrue(output.contains("newFilesCount"));
			assertTrue(output.contains("Session removed."));
			assertTrue(sessionStore.load() == null);
			assertTrue(errBytes.toString(StandardCharsets.UTF_8)
			                   .isBlank());
		}
	}

	@Test
	void errorPaths_areReportedToStderr() throws Exception {
		var outBytes = new ByteArrayOutputStream();
		var errBytes = new ByteArrayOutputStream();
		var app      = new VempainFileCliApplication(new SessionStore(), new BackendClient(), new PrintStream(outBytes), new PrintStream(errBytes));

		app.run(new String[]{"files-list", "--type", "music"});
		app.run(new String[]{"files-list", "--type", "notatype"});

		var sessionStore = new SessionStore();
		sessionStore.save("http://localhost:8080/api", "jwt-x");
		var appWithSession = new VempainFileCliApplication(sessionStore, new BackendClient(), new PrintStream(outBytes), new PrintStream(errBytes));
		appWithSession.run(new String[]{"scan"});

		var err = errBytes.toString(StandardCharsets.UTF_8);
		assertTrue(err.contains("Not logged in"));
		assertTrue(err.contains("Invalid --type"));
		assertTrue(err.contains("Provide --original-directory and/or --export-directory"));
	}

	@Test
	void runShell_processesLineAndExits() throws Exception {
		try (var server = new MockApiServer()) {
			server.registerJson("/api/files/music/paged", "{\"content\":[],\"page\":0,\"total_pages\":0,\"total_elements\":0}");

			var sessionStore = new SessionStore();
			sessionStore.save(server.baseUrl(), "jwt-5");

			var outBytes = new ByteArrayOutputStream();
			var app      = new VempainFileCliApplication(sessionStore, new BackendClient(), new PrintStream(outBytes), System.err);

			var inputs = new ArrayDeque<String>();
			inputs.add("files-list --type music");
			inputs.add("exit");
			LineReader reader = (LineReader) Proxy.newProxyInstance(
					LineReader.class.getClassLoader(),
					new Class[]{LineReader.class},
					(proxy, method, args) -> {
						if ("readLine".equals(method.getName())) {
							var next = inputs.poll();
							if (next == null) {
								throw new EndOfFileException();
							}
							return next;
						}
						if (method.getReturnType()
						          .equals(boolean.class)) {
							return false;
						}
						if (method.getReturnType()
						          .equals(int.class)) {
							return 0;
						}
						return null;
					}
			);

			app.runShell(reader);
			var output = outBytes.toString(StandardCharsets.UTF_8);
			assertTrue(output.contains("Interactive shell started"));
			assertTrue(output.contains("No files found."));
		}
	}

	@Test
	void commandCompletion_suggestsCommandNames() {
		var app        = new VempainFileCliApplication(new SessionStore(), new BackendClient(), System.out, System.err);
		var completer  = app.createCompleterForTests();
		var candidates = new ArrayList<Candidate>();
		completer.complete(null, new StubParsedLine(List.of(""), 0, ""), candidates);
		assertTrue(candidates.stream()
		                     .anyMatch(c -> "file-show".equals(c.value())));
	}

	@Test
	void usageAndParseErrors_areHandled() {
		var outBytes = new ByteArrayOutputStream();
		var errBytes = new ByteArrayOutputStream();
		var app      = new VempainFileCliApplication(new SessionStore(), new BackendClient(), new PrintStream(outBytes), new PrintStream(errBytes));

		app.run(new String[]{});
		app.run(new String[]{"--help"});
		app.run(new String[]{"files-list", "--unknown"});

		var err = errBytes.toString(StandardCharsets.UTF_8);
		assertTrue(err.contains("Was passed main parameter") || err.contains("Unknown option"));
	}

	@Test
	void fileShow_binaryContentMessageIsPrinted() throws Exception {
		try (var server = new MockApiServer()) {
			server.registerJson("/api/files/music/99", "{\"id\":99,\"filename\":\"bin.dat\",\"file_type\":\"MUSIC\",\"mimetype\":\"application/octet-stream\"}");
			server.registerBytes("/api/files/99/content", "application/octet-stream", new byte[]{1, 2, 3, 4});

			var sessionStore = new SessionStore();
			sessionStore.save(server.baseUrl(), "jwt-6");

			var outBytes = new ByteArrayOutputStream();
			var app      = new VempainFileCliApplication(sessionStore, new BackendClient(), new PrintStream(outBytes), System.err);
			app.run(new String[]{"file-show", "--type", "music", "--id", "99"});

			assertTrue(outBytes.toString(StandardCharsets.UTF_8)
			                   .contains("Binary content (4 bytes"));
		}
	}

	@Test
	void scanCompletion_withoutSessionProducesNoCandidates() {
		var app        = new VempainFileCliApplication(new SessionStore(), new BackendClient(), System.out, System.err);
		var completer  = app.createCompleterForTests();
		var candidates = new ArrayList<Candidate>();
		completer.complete(null, new StubParsedLine(List.of("scan", "--export-directory", "/ex"), 2, "/ex"), candidates);
		assertTrue(candidates.isEmpty());
	}

	@Test
	void loginWithoutToken_reportsError() throws Exception {
		try (var server = new MockApiServer()) {
			server.registerJson("/api/login", "{}");

			var outBytes = new ByteArrayOutputStream();
			var errBytes = new ByteArrayOutputStream();
			var app      = new VempainFileCliApplication(new SessionStore(), new BackendClient(), new PrintStream(outBytes), new PrintStream(errBytes));
			app.run(new String[]{"login", "--url", server.baseUrl(), "--username", "admin", "--password", "pw"});

			assertTrue(errBytes.toString(StandardCharsets.UTF_8)
			                   .contains("Login response did not include token"));
		}
	}

	private static class StubParsedLine implements ParsedLine {
		private final List<String> words;
		private final int          wordIndex;
		private final String       word;

		private StubParsedLine(List<String> words, int wordIndex, String word) {
			this.words     = words;
			this.wordIndex = wordIndex;
			this.word      = word;
		}

		@Override
		public String word() {
			return word;
		}

		@Override
		public int wordCursor() {
			return word.length();
		}

		@Override
		public int wordIndex() {
			return wordIndex;
		}

		@Override
		public List<String> words() {
			return words;
		}

		@Override
		public String line() {
			return String.join(" ", words);
		}

		@Override
		public int cursor() {
			return line().length();
		}
	}

	private static class MockApiServer implements AutoCloseable {
		private final HttpServer server;

		private MockApiServer() throws IOException {
			server = HttpServer.create(new InetSocketAddress(0), 0);
			server.start();
		}

		private void registerJson(String path, String responseBody) {
			register(path, "application/json", responseBody.getBytes(StandardCharsets.UTF_8));
		}

		private void registerBytes(String path, String contentType, byte[] responseBody) {
			register(path, contentType, responseBody);
		}

		private void register(String path, String contentType, byte[] responseBody) {
			server.createContext(path, new HttpHandler() {
				@Override
				public void handle(HttpExchange exchange) throws IOException {
					exchange.getResponseHeaders()
					        .add("Content-Type", contentType);
					exchange.sendResponseHeaders(200, responseBody.length);
					try (OutputStream os = exchange.getResponseBody()) {
						os.write(responseBody);
					}
				}
			});
		}

		private String baseUrl() {
			return "http://localhost:" + server.getAddress()
			                                   .getPort() + "/api";
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}
}

