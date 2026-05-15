package fi.poltsi.vempain.file.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VempainFileCliApplication {

	private static final Set<String> FILE_TYPES = Arrays.stream(FileTypeEnum.values())
	                                                    .filter(t -> t != FileTypeEnum.UNKNOWN)
	                                                    .map(t -> t.shortName)
	                                                    .collect(Collectors.toSet());

	private final SessionStore  sessionStore;
	private final BackendClient backendClient;
	private final PrintStream   out;
	private final PrintStream   err;

	public VempainFileCliApplication() {
		this(new SessionStore(), new BackendClient(), System.out, System.err);
	}

	VempainFileCliApplication(SessionStore sessionStore, BackendClient backendClient, PrintStream out, PrintStream err) {
		this.sessionStore  = sessionStore;
		this.backendClient = backendClient;
		this.out           = out;
		this.err           = err;
	}

	public static void main(String[] args) {
		new VempainFileCliApplication().run(args);
	}

	void run(String[] args) {
		var root         = new RootArgs();
		var login        = new LoginCommand();
		var list         = new ListCommand();
		var show         = new ShowCommand();
		var publishMusic = new PublishMusicCommand();
		var publishGps   = new PublishGpsCommand();
		var scan         = new ScanCommand();
		var logout       = new LogoutCommand();
		var shell        = new ShellCommand();

		var jc = JCommander.newBuilder()
		                   .programName("vempain-file-cli")
		                   .addObject(root)
		                   .addCommand("login", login)
		                   .addCommand("files-list", list)
		                   .addCommand("file-show", show)
		                   .addCommand("publish-music", publishMusic)
		                   .addCommand("publish-gps", publishGps)
		                   .addCommand("scan", scan)
		                   .addCommand("logout", logout)
		                   .addCommand("shell", shell)
		                   .build();

		if (args.length == 0) {
			jc.usage();
			return;
		}

		try {
			jc.parse(args);
		} catch (ParameterException e) {
			err.println(e.getMessage());
			jc.usage();
			return;
		}

		if (root.help) {
			jc.usage();
			return;
		}

		var command = jc.getParsedCommand();
		if (command == null) {
			jc.usage();
			return;
		}

		try {
			switch (command) {
				case "login" -> login(login);
				case "files-list" -> listFiles(list);
				case "file-show" -> showFile(show);
				case "publish-music" -> publishMusic();
				case "publish-gps" -> publishGps(publishGps);
				case "scan" -> scan(scan);
				case "logout" -> logout();
				case "shell" -> shell();
				default -> jc.usage();
			}
		} catch (Exception e) {
			err.println("Error: " + e.getMessage());
		}
	}

	private void login(LoginCommand command) throws Exception {
		var baseUrl  = sessionStore.normalizeBaseUrl(command.url);
		var response = backendClient.login(baseUrl, command.username, command.password);
		var token    = response.optString("token", "");
		if (token.isBlank()) {
			throw new IllegalStateException("Login response did not include token");
		}
		sessionStore.save(baseUrl, token);
		out.println("Login successful. Session saved for " + baseUrl);
	}

	private void listFiles(ListCommand command) throws Exception {
		validateType(command.type);
		var session = requireSession();

		var request = new JSONObject();
		request.put("page", command.page);
		request.put("size", command.size);
		if (command.sortBy != null) {
			request.put("sort_by", command.sortBy);
		}
		if (command.direction != null) {
			request.put("direction", command.direction.toUpperCase(Locale.ROOT));
		}
		if (command.search != null) {
			request.put("search", command.search);
		}
		request.put("case_sensitive", command.caseSensitive);

		var response = backendClient.postJson(session, "/files/" + command.type + "/paged", request);
		var content  = response.optJSONArray("content");
		if (content == null || content.isEmpty()) {
			out.println("No files found.");
			return;
		}

		out.printf("%-8s %-36s %-13s %-12s %-25s%n", "id", "filename", "file_type", "filesize", "created");
		out.println("-".repeat(100));
		for (int i = 0; i < content.length(); i++) {
			var item = content.getJSONObject(i);
			out.printf("%-8d %-36s %-13s %-12d %-25s%n",
			           item.optLong("id", 0L),
			           truncate(item.optString("filename", ""), 36),
			           truncate(item.optString("file_type", ""), 13),
			           item.optLong("filesize", 0L),
			           truncate(item.optString("created", ""), 25));
		}

		out.printf("%nPage %d/%d, total elements: %d%n",
		           response.optInt("page", 0),
		           response.has("totalPages") ? response.optInt("totalPages", response.optInt("total_pages", 0)) : response.optInt("total_pages", 0),
		           response.has("totalElements") ? response.optLong("totalElements", response.optLong("total_elements", 0L)) : response.optLong("total_elements", 0L));
	}

	private void showFile(ShowCommand command) throws Exception {
		validateType(command.type);
		var session = requireSession();

		var metadata = backendClient.getJson(session, "/files/" + command.type + "/" + command.id);
		printMetadata(metadata, command.raw);

		HttpResponse<byte[]> contentResponse = backendClient.getBytes(session, "/files/" + command.id + "/content");
		var                  contentType     = contentResponse.headers()
		                                                      .firstValue("content-type")
		                                                      .orElse("application/octet-stream");
		var                  bytes           = contentResponse.body();

		if (isTextualContent(contentType)) {
			var text   = new String(bytes, StandardCharsets.UTF_8);
			var maxLen = Math.max(0, command.contentLimit);
			if (!command.raw && text.length() > maxLen) {
				out.printf("%nContent (truncated to %d chars):%n", maxLen);
				out.println(text.substring(0, maxLen));
			} else {
				out.println("\nContent:");
				out.println(text);
			}
		} else {
			out.printf("%nBinary content (%d bytes, content-type=%s) is not displayed.%n", bytes.length, contentType);
		}
	}

	private void publishMusic() throws Exception {
		var session  = requireSession();
		var response = backendClient.postJson(session, "/data-publish/music", new JSONObject());
		out.println(response.toString(2));
	}

	private void publishGps(PublishGpsCommand command) throws Exception {
		var session = requireSession();
		var request = new JSONObject()
				.put("file_group_id", command.fileGroupId)
				.put("time_series_name", command.timeSeriesName);
		var response = backendClient.postJson(session, "/data-publish/gps-timeseries", request);
		out.println(response.toString(2));
	}

	private void scan(ScanCommand command) throws Exception {
		var session = requireSession();
		if ((command.originalDirectory == null || command.originalDirectory.isBlank())
		    && (command.exportDirectory == null || command.exportDirectory.isBlank())) {
			throw new IllegalArgumentException("Provide --original-directory and/or --export-directory");
		}

		var request = new JSONObject();
		if (command.originalDirectory != null && !command.originalDirectory.isBlank()) {
			request.put("original_directory", command.originalDirectory);
		}
		if (command.exportDirectory != null && !command.exportDirectory.isBlank()) {
			request.put("export_directory", command.exportDirectory);
		}

		var response = backendClient.postJson(session, "/scan-files", request);
		if (response.has("items") && response.get("items") instanceof JSONArray arr) {
			out.println(arr.toString(2));
		} else {
			out.println(response.toString(2));
		}
	}

	private void logout() throws Exception {
		sessionStore.clear();
		out.println("Session removed.");
	}

	private void shell() {
		var reader = LineReaderBuilder.builder()
		                              .completer(new CliCompleter())
		                              .build();
		runShell(reader);
	}

	void runShell(LineReader reader) {
		out.println("Interactive shell started. Type 'exit' to quit.");

		while (true) {
			try {
				var line = reader.readLine("vempain-file> ");
				if (line == null) {
					continue;
				}
				var trimmed = line.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				if ("exit".equalsIgnoreCase(trimmed) || "quit".equalsIgnoreCase(trimmed)) {
					break;
				}
				run(splitArgs(trimmed));
			} catch (UserInterruptException ignored) {
			} catch (EndOfFileException ignored) {
				break;
			}
		}
	}

	private SessionStore.Session requireSession() {
		var session = sessionStore.load();
		if (session == null) {
			throw new IllegalStateException("Not logged in. Run: login --url <url> --username <user> --password <pass>");
		}
		return session;
	}

	private void validateType(String type) {
		if (type == null || !FILE_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
			throw new IllegalArgumentException("Invalid --type. Allowed values: " + String.join(", ", FILE_TYPES));
		}
	}

	private boolean isTextualContent(String contentType) {
		var lower = contentType.toLowerCase(Locale.ROOT);
		return lower.startsWith("text/")
		       || lower.contains("application/json")
		       || lower.contains("application/xml")
		       || lower.contains("application/javascript");
	}

	private void printMetadata(JSONObject metadata, boolean raw) {
		out.println("Metadata:");
		if (raw) {
			out.println(metadata.toString(2));
			return;
		}

		out.println("  [Identity]");
		out.println("    id: " + metadata.optLong("id", 0));
		out.println("    filename: " + metadata.optString("filename", ""));
		out.println("    file_type: " + metadata.optString("file_type", ""));
		out.println("    mimetype: " + metadata.optString("mimetype", ""));
		out.println("    filesize: " + metadata.optLong("filesize", 0));
		out.println("  [Timestamps]");
		out.println("    created: " + metadata.optString("created", ""));
		out.println("    modified: " + metadata.optString("modified", ""));
		out.println("    original_datetime: " + metadata.optString("original_datetime", ""));
	}

	private String truncate(String value, int maxLength) {
		if (value == null) {
			return "";
		}
		if (value.length() <= maxLength || maxLength < 4) {
			return value;
		}
		return value.substring(0, maxLength - 3) + "...";
	}

	private String[] splitArgs(String commandLine) {
		List<String> tokens  = new ArrayList<>();
		Matcher      matcher = Pattern.compile("\\\"([^\\\"]*)\\\"|'([^']*)'|(\\S+)")
		                              .matcher(commandLine);
		while (matcher.find()) {
			if (matcher.group(1) != null) {
				tokens.add(matcher.group(1));
			} else if (matcher.group(2) != null) {
				tokens.add(matcher.group(2));
			} else {
				tokens.add(matcher.group(3));
			}
		}
		return tokens.toArray(new String[0]);
	}

	private class CliCompleter implements Completer {

		private final StringsCompleter commandCompleter = new StringsCompleter(
				"login", "files-list", "file-show", "publish-music", "publish-gps", "scan", "logout", "shell", "exit", "quit"
		);

		@Override
		public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
			if (line.wordIndex() == 0) {
				commandCompleter.complete(reader, line, candidates);
				return;
			}

			var words = line.words();
			if (words.isEmpty()) {
				return;
			}
			var command = words.get(0);

			if (("files-list".equals(command) || "file-show".equals(command)) && wantsTypeCompletion(words, line.wordIndex())) {
				FILE_TYPES.stream()
				          .sorted()
				          .forEach(type -> candidates.add(new Candidate(type)));
				return;
			}

			if ("scan".equals(command)) {
				completeScanPath(words, line.wordIndex(), candidates, line.word());
			}
		}

		private boolean wantsTypeCompletion(List<String> words, int wordIndex) {
			if (wordIndex <= 0) {
				return false;
			}
			var previous = words.get(Math.max(0, wordIndex - 1));
			return "--type".equals(previous) || "-t".equals(previous);
		}

		private void completeScanPath(List<String> words, int wordIndex, List<Candidate> candidates, String currentWord) {
			if (wordIndex <= 0) {
				return;
			}
			var    previous = words.get(Math.max(0, wordIndex - 1));
			String completionType;
			if ("--original-directory".equals(previous) || "-o".equals(previous)) {
				completionType = "ORIGINAL";
			} else if ("--export-directory".equals(previous) || "-e".equals(previous)) {
				completionType = "EXPORTED";
			} else {
				return;
			}

			var session = sessionStore.load();
			if (session == null) {
				return;
			}

			var path    = (currentWord == null || currentWord.isBlank()) ? "/" : currentWord;
			var request = new JSONObject().put("path", path)
			                              .put("type", completionType);
			try {
				var response    = backendClient.postJson(session, "/path-completion", request);
				var completions = response.optJSONArray("completions");
				if (completions == null) {
					return;
				}
				for (int i = 0; i < completions.length(); i++) {
					candidates.add(new Candidate(completions.getString(i)));
				}
			} catch (Exception ignored) {
			}
		}
	}

	Completer createCompleterForTests() {
		return new CliCompleter();
	}

	private static class RootArgs {
		@Parameter(names = {"-h", "--help"}, help = true, description = "Show help")
		boolean help;
	}

	@Parameters(commandDescription = "Authenticate and store session")
	private static class LoginCommand {
		@Parameter(names = {"--url"}, required = true, description = "Backend base URL (e.g. http://localhost:8080/api)")
		String url;
		@Parameter(names = {"--username"}, required = true, description = "Username")
		String username;
		@Parameter(names = {"--password"}, required = true, password = true, description = "Password")
		String password;
	}

	@Parameters(commandDescription = "List files for one file type with paging")
	private static class ListCommand {
		@Parameter(names = {"-t", "--type"}, required = true, description = "File type in lowercase")
		String  type;
		@Parameter(names = {"-p", "--page"}, description = "Page number (0-based)")
		int     page          = 0;
		@Parameter(names = {"-s", "--size"}, description = "Page size")
		int     size          = 25;
		@Parameter(names = {"--sort-by"}, description = "Sort field")
		String  sortBy        = "filename";
		@Parameter(names = {"--direction"}, description = "Sort direction ASC|DESC")
		String  direction     = "ASC";
		@Parameter(names = {"--search"}, description = "Search query")
		String  search;
		@Parameter(names = {"--case-sensitive"}, description = "Case-sensitive search")
		boolean caseSensitive = false;
	}

	@Parameters(commandDescription = "Show file metadata and content for one typed file")
	private static class ShowCommand {
		@Parameter(names = {"-t", "--type"}, required = true, description = "File type in lowercase")
		String  type;
		@Parameter(names = {"-i", "--id"}, required = true, description = "File ID")
		long    id;
		@Parameter(names = {"--raw"}, description = "Show raw metadata JSON and do not truncate text content")
		boolean raw;
		@Parameter(names = {"--content-limit"}, description = "Maximum number of chars shown for textual content")
		int     contentLimit = 8_192;
	}

	@Parameters(commandDescription = "Generate and publish music dataset")
	private static class PublishMusicCommand {
	}

	@Parameters(commandDescription = "Generate and publish GPS time-series dataset")
	private static class PublishGpsCommand {
		@Parameter(names = {"--file-group-id"}, required = true, description = "File group ID")
		long   fileGroupId;
		@Parameter(names = {"--time-series-name"}, required = true, description = "Time-series identifier")
		String timeSeriesName;
	}

	@Parameters(commandDescription = "Trigger backend file scan")
	private static class ScanCommand {
		@Parameter(names = {"-o", "--original-directory"}, description = "Original directory path")
		String originalDirectory;
		@Parameter(names = {"-e", "--export-directory"}, description = "Export directory path")
		String exportDirectory;
	}

	@Parameters(commandDescription = "Clear stored session")
	private static class LogoutCommand {
	}

	@Parameters(commandDescription = "Start interactive shell (supports tab completion)")
	private static class ShellCommand {
	}
}

