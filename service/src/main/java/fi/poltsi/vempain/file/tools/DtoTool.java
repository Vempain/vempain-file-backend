package fi.poltsi.vempain.file.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DtoTool {
	private static final ObjectMapper MAPPER = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
			.setSerializationInclusion(JsonInclude.Include.NON_NULL);

	/**
	 * Convert any object (POJO, Map, array, DTO, etc.) to its JSON string representation.
	 * Throws RuntimeException on failure.
	 */
	public static String toJson(Object obj) {
		if (obj == null) {
			return "null";
		}
		try {
			return MAPPER.writeValueAsString(obj);
		} catch (Exception e) {
			throw new RuntimeException("Failed to serialize object to JSON", e);
		}
	}
}
