package fi.poltsi.vempain.file.tools;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
					"60 deg 10' 30.00\"|N|60.175",
					"60 deg 10' 30.00\"|S|-60.175",
					"24 deg 58' 00.0\"|E|24.9666666667",
					"24 deg 58' 00.0\"|W|-24.9666666667",
					"60 deg 10' 30.00\"|<null>|<null>",
					"60 deg 10' 30.00\"|Z|<null>",
					"   |N|<null>",
					"<null>|N|<null>",
					"invalid|N|<null>"
			},
			nullValues = "<null>",
			delimiter = '|'
	)
	void gpsConversion(String coordinate, Character cardinal, Double expected) {
		var actual = MetadataTool.convertGpsCoordinateStringToDouble(coordinate, cardinal);

		if (expected == null) {
			assertNull(actual);
		} else {
			assertNotNull(actual);
			assertEquals(expected, actual, DELTA);
		}
	}

	@ParameterizedTest
	@DisplayName("extractGpsData branch coverage")
	@CsvSource(
			value = {
					// scenario|Composite.GPSLocation    |GPS.GPSLatitudeRef|GPS.GPSLatitude|Composite.GPSLatitude|GPS.GPSLongitudeRef|GPS
					// .GPSLongitude|Composite.GPSLongitude|expLat|expLatRef|expLon|expLonRef
					"A         |60 deg 10' 30.00\" N, 24 deg 58' 00.0\" E|<null>|<null>            |<null>              |<null>|<null>|<null>|60.175|N|24.9666666667|E",
					"B         |60 deg 10' 30.00\" X, 24 deg 58' 00.0\" Y|<null>|<null>            |<null>              |<null>|<null>|<null>|<null>|<null>|<null>|<null>",
					"C         |<null>                                   |N     |60 deg 10' 30.00\"|<null>              |E|24 deg 58' 00.0\"|<null>|60.175|N|24.9666666667|E",
					"D         |<null>                                   |<null>|<null>            |60 deg 10' 30.00\" S|<null>|<null>|24 deg 58' 00.0\" S|-60.175|S|-24.9666666667|S",
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
									 Double expectedLat,
									 Character expectedLatRef,
									 Double expectedLon,
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
			assertEquals(expectedLat, entity.getLatitude(), DELTA, "Scenario " + scenario + " latitude");
			assertEquals(expectedLatRef, entity.getLatitudeRef(), "Scenario " + scenario + " latitudeRef");
		}

		if (expectedLon == null) {
			assertNull(entity.getLongitude(), "Scenario " + scenario + " longitude");
			assertNull(entity.getLongitudeRef(), "Scenario " + scenario + " longitudeRef");
		} else {
			assertNotNull(entity.getLongitude(), "Scenario " + scenario);
			assertEquals(expectedLon, entity.getLongitude(), DELTA, "Scenario " + scenario + " longitude");
			assertEquals(expectedLonRef, entity.getLongitudeRef(), "Scenario " + scenario + " longitudeRef");
		}
	}
}
