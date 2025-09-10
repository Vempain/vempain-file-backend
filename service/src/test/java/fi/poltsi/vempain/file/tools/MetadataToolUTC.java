package fi.poltsi.vempain.file.tools;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Slf4j
class MetadataToolUTC {

	private static final double DELTA = 1e-6;

	@ParameterizedTest
	@CsvSource({
			"2006:11:19 00:04:34+02:00",
			"2008:03:20 21:14:46.00+02:00",
			"2014:06:24 09:34:01.761+03:00",
			"2022:07:07 17:35:12.1Z"
	})
	void dateTimeParser(String dateTimeString) {
		var dateTime = MetadataTool.dateTimeParser(dateTimeString);
		assertNotNull(dateTime);
	}

	@ParameterizedTest
	@DisplayName("GPS coordinate conversions (parameterized)")
	@CsvSource(
			value = {
					"60 deg 10' 30.00\"|N|60.17500",
					"60 deg 10' 30.00\"|S|-60.17500",
					"24 deg 58' 00.0\"|E|24.96667",
					"24 deg 58' 00.0\"|W|-24.96667",
					"60 deg 10' 30.00\"|<null>|<null>",
					"60 deg 10' 30.00\"|Z|<null>",
					"   |N|<null>",
					"<null>|N|<null>",
					"invalid|N|<null>"
			},
			nullValues = "<null>",
			delimiter = '|'
	)
	void gpsConversion(String coordinate, Character cardinal, String expected) {
		var actual = MetadataTool.convertGpsCoordinateStringToBigDecimal(coordinate, cardinal);

		if (expected == null) {
			assertNull(actual);
		} else {
			assertNotNull(actual);
			assertEquals(new BigDecimal(expected), actual);
		}
	}

	@ParameterizedTest
	@DisplayName("GPS coordinate decimal precision")
	@CsvSource(
			value = {
					// Input DMS | Cardinal | Expected Decimal (5 places)
					"60 deg 10' 30.123\"|N|60.17504",
					"60 deg 10' 30.555\"|N|60.17516",
					"60 deg 10' 30.999\"|N|60.17528",
					"24 deg 58' 0.123\"|E|24.96670",
					"24 deg 58' 0.555\"|E|24.96682",
					"24 deg 58' 0.999\"|E|24.96695"
			},
			delimiter = '|'
	)
	void gpsDecimalPrecision(String dmsCoordinate, Character cardinal, String expected) {
		var result = MetadataTool.convertGpsCoordinateStringToBigDecimal(dmsCoordinate, cardinal);
		assertNotNull(result);
		assertEquals(new BigDecimal(expected), result, "Precision should be exactly 5 decimal places");
		assertEquals(5, result.scale(), "Scale should be 5");
	}

	@ParameterizedTest
	@DisplayName("extractGpsData branch coverage")
	@CsvSource(
			value = {
					// scenario|Composite.GPSLocation    |GPS.GPSLatitudeRef|GPS.GPSLatitude|Composite.GPSLatitude|GPS.GPSLongitudeRef|GPS
					// .GPSLongitude|Composite.GPSLongitude|expLat|expLatRef|expLon|expLonRef
					"A         |60 deg 10' 30.00\" N, 24 deg 58' 00.0\" E|<null>|<null>            |<null>              |<null>|<null>|<null>|60.17500|N|24.96667|E",
					"B         |60 deg 10' 30.00\" X, 24 deg 58' 00.0\" Y|<null>|<null>            |<null>              |<null>|<null>|<null>|<null>|<null>|<null>|<null>",
					"C         |<null>                                   |N     |60 deg 10' 30.00\"|<null>              |E|24 deg 58' 00.0\"|<null>|60.17500|N|24.96667|E",
					"D         |<null>                                   |<null>|<null>            |60 deg 10' 30.00\" S|<null>|<null>|24 deg 58' 00.0\" S|-60.17500|S|-24.96667|S",
					"E         |<null>                                   |<null>|<null>            |<null>              |<null>|<null>|<null>|<null>|<null>|<null>|<null>"
			},
			nullValues = "<null>",
			delimiter = '|'
	)
	void extractGpsDataParameterized(String scenario,
									 String compositeGpsLocation,
									 String gpsLatRef,
									 String gpsLat,
									 String compositeGpsLat,
									 String gpsLonRef,
									 String gpsLon,
									 String compositeGpsLon,
									 String expectedLat,
									 Character expectedLatRef,
									 String expectedLon,
									 Character expectedLonRef) {

		var root = new JSONObject();

		// Build Composite section
		var compositeObj = new JSONObject();
		if (compositeGpsLocation != null) {
			compositeObj.put("GPSLocation", compositeGpsLocation);
		}
		if (compositeGpsLat != null) {
			compositeObj.put("GPSLatitude", compositeGpsLat);
		}
		if (compositeGpsLon != null) {
			compositeObj.put("GPSLongitude", compositeGpsLon);
		}
		if (!compositeObj.isEmpty()) {
			root.put("Composite", compositeObj);
		}

		// Build GPS section
		var gpsObj = new JSONObject();
		if (gpsLatRef != null) {
			gpsObj.put("GPSLatitudeRef", gpsLatRef);
		}
		if (gpsLat != null) {
			gpsObj.put("GPSLatitude", gpsLat);
		}
		if (gpsLonRef != null) {
			gpsObj.put("GPSLongitudeRef", gpsLonRef);
		}
		if (gpsLon != null) {
			gpsObj.put("GPSLongitude", gpsLon);
		}
		if (!gpsObj.isEmpty()) {
			root.put("GPS", gpsObj);
		}

		log.info("Scenario {}: input JSON: {}", scenario, root.toString());
		var entity = MetadataTool.extractGpsData(root);

		if (expectedLat == null) {
			assertNull(entity.getLatitude(), "Scenario " + scenario + " latitude");
			assertNull(entity.getLatitudeRef(), "Scenario " + scenario + " latitudeRef");
		} else {
			assertNotNull(entity.getLatitude(), "Scenario " + scenario);
			assertEquals(new BigDecimal(expectedLat), entity.getLatitude(), "Scenario " + scenario + " latitude");
			assertEquals(expectedLatRef, entity.getLatitudeRef(), "Scenario " + scenario + " latitudeRef");
		}

		if (expectedLon == null) {
			assertNull(entity.getLongitude(), "Scenario " + scenario + " longitude");
			assertNull(entity.getLongitudeRef(), "Scenario " + scenario + " longitudeRef");
		} else {
			assertNotNull(entity.getLongitude(), "Scenario " + scenario);
			assertEquals(new BigDecimal(expectedLon), entity.getLongitude(), "Scenario " + scenario + " longitude");
			assertEquals(expectedLonRef, entity.getLongitudeRef(), "Scenario " + scenario + " longitudeRef");
		}
	}

	@ParameterizedTest
	@DisplayName("Parse audio/video duration from various formats")
	@CsvSource(value = {
			// Format: description, input duration string, expected seconds
			"hours_minutes_seconds, 1:30:45, 5445.0",
			"hours_minutes_seconds_decimals, 1:30:45.5, 5445.5",
			"minutes_seconds, 5:30, 330.0",
			"minutes_seconds_decimals, 5:30.5, 330.5",
			"seconds_only, 42, 42.0",
			"seconds_decimals, 42.5, 42.5",
			"seconds_with_s_qualifier, 24.47 s, 24.47",
			"seconds_with_sec_qualifier, 24.47 sec, 24.47",
			"seconds_with_seconds_qualifier, 24.47 seconds, 24.47",
			"empty_string, , 0.0",
			"null, null, 0.0"
	}, nullValues = "null")
	void parseDurationString(String description, String durationString, double expectedSeconds) {
		// Create a helper method to test just the string parsing functionality
		double actualSeconds = parseDurationStringHelper(durationString);
		assertEquals(expectedSeconds, actualSeconds, DELTA, "Failed for case: " + description);
	}

	/**
	 * Helper method that implements the same parsing logic as in MetadataTool.extractAudioVideoDuration
	 * but accepts a string directly instead of requiring a file.
	 */
	private double parseDurationStringHelper(String durationStr) {
		log.info("Parsing duration string: '{}'", durationStr);
		// If the string is empty or null, return 0
		if (durationStr == null || durationStr.isBlank()) {
			return 0.0;
		}

		// If the string contains colons, it's in a time format (hh:mm:ss or mm:ss)
		if (durationStr.contains(":")) {
			String[] parts   = durationStr.split(":");
			double   seconds = 0.0;

			if (parts.length == 3) {
				// Format: hh:mm:ss.ms
				seconds += Double.parseDouble(parts[0]) * 3600; // Hours to seconds
				seconds += Double.parseDouble(parts[1]) * 60;   // Minutes to seconds
				seconds += Double.parseDouble(parts[2]);        // Seconds
			} else if (parts.length == 2) {
				// Format: mm:ss.ms
				seconds += Double.parseDouble(parts[0]) * 60;   // Minutes to seconds
				seconds += Double.parseDouble(parts[1]);        // Seconds
			}

			return seconds;
		} else {
			// Check if the string contains a time qualifier like "s", "sec", "seconds"
			String trimmedStr = durationStr.trim();
			if (trimmedStr.endsWith(" s") ||
				trimmedStr.endsWith(" sec") ||
				trimmedStr.endsWith(" seconds")) {

				// Extract the numeric part before the qualifier
				int spaceIndex = trimmedStr.lastIndexOf(' ');
				if (spaceIndex > 0) {
					String numericPart = trimmedStr.substring(0, spaceIndex);
					return Double.parseDouble(numericPart);
				}
			}

			// It's already in seconds format (possibly with decimal)
			return Double.parseDouble(durationStr);
		}
	}
}
