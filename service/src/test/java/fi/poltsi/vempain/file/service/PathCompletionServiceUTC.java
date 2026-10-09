package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.PathCompletionEnum;
import fi.poltsi.vempain.file.api.request.PathCompletionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PathCompletionServiceUTC {

	private static PathCompletionService service(Path root, String locale) {
		var service = new PathCompletionService();
		ReflectionTestUtils.setField(service, "originalRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "exportedRootDirectory", root.toString());
		ReflectionTestUtils.setField(service, "collationLocale", locale);
		return service;
	}

	@Test
	void listsSubDirectories(@TempDir Path root) throws Exception {
		Files.createDirectories(root.resolve("album"));
		var service = service(root, "fi");

		var response = service.completePath(new PathCompletionRequest("/", PathCompletionEnum.ORIGINAL));

		assertThat(response.getCompletions()).contains("/album");
	}

	@Test
	void subDirectoriesAreAlphabeticalRegardlessOfModificationTime(@TempDir Path root) throws Exception {
		// Created and touched in a deliberately non-alphabetical order so that a filesystem order would differ
		var names = List.of("zebra", "Öljy", "album", "Åland", "ärsyke", "Banana", "ananas");
		var stamp = Instant.now()
						   .minus(1, ChronoUnit.DAYS);
		for (var name : names) {
			var dir = Files.createDirectories(root.resolve(name));
			Files.setLastModifiedTime(dir, FileTime.from(stamp));
			stamp = stamp.plus(1, ChronoUnit.HOURS);
		}
		var service = service(root, "fi");

		var response = service.completePath(new PathCompletionRequest("/", PathCompletionEnum.ORIGINAL));

		// Finnish/Swedish alphabet: å, ä and ö come after z; the case does not change the position
		assertThat(response.getCompletions()).containsExactly("/album", "/ananas", "/Banana", "/zebra", "/Åland", "/ärsyke", "/Öljy");
	}

	@Test
	void nestedAndExportCompletionsAreSortedToo(@TempDir Path root) throws Exception {
		Files.createDirectories(root.resolve("events/öinen"));
		Files.createDirectories(root.resolve("events/aamu"));
		Files.createDirectories(root.resolve("events/Ilta"));
		var service = service(root, "fi");

		var nested = service.completePath(new PathCompletionRequest("/events", PathCompletionEnum.ORIGINAL));
		assertThat(nested.getCompletions()).containsExactly("/events/aamu", "/events/Ilta", "/events/öinen");

		var exported = service.completePath(new PathCompletionRequest("/events/", PathCompletionEnum.EXPORTED));
		assertThat(exported.getCompletions()).containsExactly("/events/aamu", "/events/Ilta", "/events/öinen");
	}

	@Test
	void prefixMatchesAreSortedWithNonAsciiPrefixes(@TempDir Path root) throws Exception {
		Files.createDirectories(root.resolve("äiti"));
		Files.createDirectories(root.resolve("Ääni"));
		Files.createDirectories(root.resolve("äänestys"));
		Files.createDirectories(root.resolve("aamu"));
		var service = service(root, "fi");

		var response = service.completePath(new PathCompletionRequest("/ä", PathCompletionEnum.ORIGINAL));

		// Prefix matching stays case-sensitive (Ääni is not offered for "ä"); within the matches ä sorts after i, so äiti precedes äänestys
		assertThat(response.getCompletions()).containsExactly("/äiti", "/äänestys");
	}

	@Test
	void collationLocaleIsConfigurable(@TempDir Path root) throws Exception {
		for (var name : List.of("zebra", "ärsyke", "album")) {
			Files.createDirectories(root.resolve(name));
		}

		// English collation treats ä as a variant of a
		var english = service(root, "en").completePath(new PathCompletionRequest("/", PathCompletionEnum.ORIGINAL));
		assertThat(english.getCompletions()).containsExactly("/album", "/ärsyke", "/zebra");

		// Missing configuration falls back to the Finnish alphabet
		var fallback = service(root, null).completePath(new PathCompletionRequest("/", PathCompletionEnum.ORIGINAL));
		assertThat(fallback.getCompletions()).containsExactly("/album", "/zebra", "/ärsyke");
	}
}
