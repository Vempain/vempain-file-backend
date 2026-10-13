package fi.poltsi.vempain.file.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The public enums of the api module are carried as their constant names in JSON; this pins every value so a rename or reorder is a
 * visible contract change.
 */
class ApiEnumJTC {
	private final JsonMapper mapper = JsonMapper.builder()
												.build();

	@Test
	void taskTypesArePinned() throws Exception {
		assertEquals(List.of("PUBLISH_FILE", "PUBLISH_FILES", "PUBLISH_FILE_GROUP", "PUBLISH_ALL_FILE_GROUPS", "SCAN_DIRECTORIES", "PUBLISH_MUSIC_DATA",
							 "PUBLISH_GPS_TIME_SERIES", "TAG_REMOVE_FROM_ALL", "TAG_REPLACE_ACROSS_ALL", "TAG_RENAME_ACROSS_ALL", "METADATA_UPDATE_VALUE"),
					 names(TaskTypeEnum.values()));
		for (var type : TaskTypeEnum.values()) {
			assertRoundTrip(TaskTypeEnum.class, type);
			assertEquals(type, TaskTypeEnum.valueOf(type.name()));
		}
	}

	@Test
	void guardTypesArePinned() throws Exception {
		assertEquals(List.of("SQUARE", "CIRCLE"), names(GuardTypeEnum.values()));
		assertRoundTrip(GuardTypeEnum.class, GuardTypeEnum.CIRCLE);
		assertEquals(GuardTypeEnum.SQUARE, GuardTypeEnum.valueOf("SQUARE"));
	}

	@Test
	void pathCompletionTypesArePinned() throws Exception {
		assertEquals(List.of("ORIGINAL", "EXPORTED"), names(PathCompletionEnum.values()));
		assertRoundTrip(PathCompletionEnum.class, PathCompletionEnum.EXPORTED);
		assertEquals(PathCompletionEnum.ORIGINAL, PathCompletionEnum.valueOf("ORIGINAL"));
	}

	private static List<String> names(Enum<?>[] values) {
		return Arrays.stream(values)
					 .map(Enum::name)
					 .toList();
	}

	private <E extends Enum<E>> void assertRoundTrip(Class<E> type, E value) throws Exception {
		String json = mapper.writeValueAsString(value);
		assertEquals("\"" + value.name() + "\"", json);
		assertEquals(value, mapper.readValue(json, type));
	}
}
