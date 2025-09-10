package fi.poltsi.vempain.file.tools;


import com.fasterxml.jackson.databind.ObjectMapper;
import fi.poltsi.vempain.file.entity.GpsLocationEntity;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

import static java.util.Map.entry;

@Slf4j
public class MetadataTool {
	private static final String COMPOSITE_KEY      = "Composite";
	private static final String EXIF_IFD_KEY = "ExifIFD";
	private static final String EXIF_KEY           = "EXIF";
	private static final String FILE_KEY     = "File";
	private static final String GPS_KEY      = "GPS";
	private static final String IFD0_KEY     = "IFD0";
	private static final String IPTC_KEY           = "IPTC";
	private static final String SUBIFD_1_KEY       = "SUBIFD1";
	private static final String SUBIFD_2_KEY       = "SUBIFD2";
	private static final String SUBIFD_3_KEY       = "SUBIFD3";
	private static final String SUBIFD_KEY         = "SUBIFD";
	private static final String SYSTEM_KEY   = "System";
	private static final String XMP_DC_KEY         = "XMP-dc";
	private static final String XMP_EXIF_KEY = "XMP-exif";
	private static final String XMP_IPTC_CORE_KEY  = "XMP-iptcCore";
	private static final String XMP_KEY            = "XMP";
	private static final String XMP_LR_KEY         = "XMP-lr";
	private static final String XMP_PHOTOSHOP_KEY  = "XMP-photoshop";
	private static final String XMP_XMPMM_KEY      = "XMP-xmpMM";
	private static final String XMP_XMP_KEY        = "XMP-xmp";
	private static final String XMP_XMP_RIGHTS_KEY = "XMP-xmpRights";

	private static final String BITS_PER_SAMPLE_FIELD      = "BitsPerSample";
	private static final String COMPOSITE_IMAGE_SIZE_FIELD = "ImageSize";
	private static final String CREATE_DATE_FIELD          = "CreateDate";
	private static final String DATETIME_ORIGINAL_FIELD    = "DateTimeOriginal";
	private static final String IPTC_CAPTION_FIELD         = "Caption-Abstract";
	private static final String IPTC_KEYWORD_FIELD         = "Keywords";
	private static final String MIMETYPE_FIELD             = "MIMEType";
	private static final String SUBIFD_IMAGE_HEIGHT_FIELD  = "ImageHeight";
	private static final String SUBIFD_IMAGE_WIDTH_FIELD   = "ImageWidth";
	private static final String XMP_DESCRIPTION_FIELD      = "Description";
	private static final String X_RESOLUTION_FIELD         = "XResolution";

	// GPS coordinate precision
	private static final int GPS_DECIMAL_PRECISION = 5;

	public static String extractMetadataJson(File file) throws IOException {
		return runExifTool(file, "-a", "-u", "-ee", "-api", "RequestAll=3", "-g1", "-J");
	}

	public static JSONObject extractMetadataJsonObject(File file) throws IOException {
		var metadata = extractMetadataJson(file);

		if (metadata == null || metadata.isBlank()) {
			log.warn("No metadata found for file: {}", file.getAbsolutePath());
			return null;
		}

		return metadataToJsonObject(metadata);
	}

	public static Dimension extractImageResolution(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		// First we try to extract the resolution from the Composite section, here it should be like "widthxheight"
		locations.put(COMPOSITE_KEY, List.of(COMPOSITE_IMAGE_SIZE_FIELD));
		var resolution = extractJsonString(jsonObject, locations);

		if (resolution != null
			&& !resolution.isBlank()
			&& resolution.contains("x")) {
			var parts = resolution.split("x");

			if (parts.length == 2) {
				try {
					int width  = Integer.parseInt(parts[0].trim());
					int height = Integer.parseInt(parts[1].trim());
					return new Dimension(width, height);
				} catch (NumberFormatException e) {
					log.error("Failed to parse image resolution: {}", resolution, e);
				}
			}
		}

		// Next we try to extract the resolution from the SubIFD section, here width and height are separate fields
		locations.put(SUBIFD_KEY, List.of(SUBIFD_IMAGE_HEIGHT_FIELD));
		var ifdHeight = extractJsonNumber(jsonObject, locations);

		if (ifdHeight != null) {
			locations.put(SUBIFD_KEY, List.of(SUBIFD_IMAGE_WIDTH_FIELD));
			var ifdWidth = extractJsonNumber(jsonObject, locations);

			if (ifdWidth != null) {
				return new Dimension(ifdWidth.intValue(), ifdHeight.intValue());
			}
		}

		return null;
	}

	/**
	 * Extract the image color depth from the given metadata in JSON format
	 *
	 * @param jsonObject Extracted JSON formatted metadata (from @MetadataTools.getMetadataAsJSON())
	 * @return int value retrieved, or 8 if none were found or parsing failed. 8 is a good default for most images.
	 */
	public static int extractImageColorDepth(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(SUBIFD_KEY, List.of(BITS_PER_SAMPLE_FIELD));
		locations.put(IFD0_KEY, List.of(BITS_PER_SAMPLE_FIELD));

		// Let's try to extract it as a string, as sometimes it might be stored as a triplet
		var colorDepthString = extractJsonString(jsonObject, locations);

		if (colorDepthString != null) {
			// Match any triplet format like "8 8 8" or "8, 8, 8" or "16 16 16"
			colorDepthString = colorDepthString.replace(",", " ")
											   .trim();
			// Then calculate the total color depth by summing the individual channel depths
			if (colorDepthString.matches("(\\d+\\s+){2}\\d+")) {
				var channelBits = colorDepthString.split("\\s+");
				int total       = 0;

				for (String channelBit : channelBits) {
					try {
						total += Integer.parseInt(channelBit);
					} catch (NumberFormatException e) {
						log.error("Failed to parse channel bit part: {}", channelBit, e);
					}
				}
				if (total > 0) {
					return total;
				}
			}

			try {
				return Integer.parseInt(colorDepthString);
			} catch (Exception e) {
				log.error("Failed to parse color depth from string: {}", colorDepthString, e);
			}
		}

		// If that fails, let's try to extract it as a number
		var colorDepthNumber = extractJsonNumber(jsonObject, locations);

		if (colorDepthNumber != null) {
			return colorDepthNumber.intValue();
		}

		// Default to 8 if nothing found
		return 8;
	}

	public static int extractImageDpi(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(SUBIFD_KEY, List.of(X_RESOLUTION_FIELD));
		var tagValue = extractJsonNumber(jsonObject, locations);

		if (tagValue == null) {
			log.warn("No XResolution found in metadata");
			return 72; // Default DPI if not found
		}

		return tagValue.intValue();
	}

	public static double extractFrameRate(File file) throws IOException {
		var output = runExifTool(file, "-VideoFrameRate");
		return Double.parseDouble(getTagValue(output, "VideoFrameRate"));
	}

	public static String extractVideoCodec(File file) throws IOException {
		var output = runExifTool(file, "-VideoCodec");
		return getTagValue(output, "VideoCodec");
	}

	public static double extractAudioVideoDuration(File file) throws IOException {
		var output = runExifTool(file, "-Duration");
		var durationStr = getTagValue(output, "Duration");

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

	public static int extractAudioBitRate(File file) throws IOException {
		var output = runExifTool(file, "-AudioBitrate");
		return Integer.parseInt(getTagValue(output, "AudioBitrate"));
	}

	public static int extractAudioSampleRate(File file) throws IOException {
		var output = runExifTool(file, "-AudioSampleRate");
		return Integer.parseInt(getTagValue(output, "AudioSampleRate"));
	}

	public static String extractAudioCodec(File file) throws IOException {
		var output = runExifTool(file, "-AudioCodec");
		return getTagValue(output, "AudioCodec");
	}

	public static int extractAudioChannels(File file) throws IOException {
		var output = runExifTool(file, "-AudioChannels");
		return Integer.parseInt(getTagValue(output, "AudioChannels"));
	}

	public static int extractDocumentPageCount(File file) throws IOException {
		var output = runExifTool(file, "-PageCount");
		return Integer.parseInt(getTagValue(output, "PageCount"));
	}

	public static String extractDocumentFormat(File file) throws IOException {
		var output = runExifTool(file, "-FileType");
		return getTagValue(output, "FileType");
	}

	public static int extractVectorLayersCount(File file) throws IOException {
		var output = runExifTool(file, "-Layers");
		return Integer.parseInt(getTagValue(output, "Layers"));
	}

	public static Dimension extractXYResolution(File file) throws IOException {
		var output = runExifTool(file, "-ImageWidth", "-ImageHeight");
		var width  = Integer.parseInt(getTagValue(output, "ImageWidth"));
		var height = Integer.parseInt(getTagValue(output, "ImageHeight"));
		return new Dimension(width, height);
	}

	public static boolean extractIconIsScalable(File file) throws IOException {
		var output = runExifTool(file, "-Scalable");
		return Boolean.parseBoolean(getTagValue(output, "Scalable"));
	}

	public static String extractFontFamily(File file) throws IOException {
		var output = runExifTool(file, "-FontFamily");
		return getTagValue(output, "FontFamily");
	}

	public static String extractFontWeight(File file) throws IOException {
		var output = runExifTool(file, "-FontWeight");
		return getTagValue(output, "FontWeight");
	}

	public static String extractFontStyle(File file) throws IOException {
		var output = runExifTool(file, "-FontStyle");
		return getTagValue(output, "FontStyle");
	}

	public static String extractArchiveCompressionMethod(File file) throws IOException {
		var output = runExifTool(file, "-Compression");
		return getTagValue(output, "Compression");
	}

	public static long extractArchiveUncompressedSize(File file) throws IOException {
		var output = runExifTool(file, "-UncompressedSize");
		return Long.parseLong(getTagValue(output, "UncompressedSize"));
	}

	public static int extractArchiveContentCount(File file) throws IOException {
		var output = runExifTool(file, "-ContentCount");
		return Integer.parseInt(getTagValue(output, "ContentCount"));
	}

	public static boolean extractArchiveIsEncrypted(File file) throws IOException {
		var output = runExifTool(file, "-Encrypted");
		return Boolean.parseBoolean(getTagValue(output, "Encrypted"));
	}

	/**
	 * Extract the comment/description field from given metadata
	 *
	 * @param jsonObject Extracted JSON formatted metadata (from @MetadataTools.getMetadataAsJSON())
	 * @return String value retrieved, or null if none were found
	 */
	public static String extractDescription(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(XMP_KEY, List.of(XMP_DESCRIPTION_FIELD));
		locations.put(IPTC_KEY, List.of(IPTC_CAPTION_FIELD));

		return extractJsonString(jsonObject, locations);
	}

	/**
	 * Extract the mimetype from the given metadata
	 *
	 * @param jsonObject Extracted JSON formatted metadata (from @MetadataTools.getMetadataAsJSON())
	 * @return String value retrieved, or null if none were found
	 */
	public static String extractMimetype(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(FILE_KEY, List.of(MIMETYPE_FIELD));
		locations.put(XMP_KEY, List.of(MIMETYPE_FIELD));

		return extractJsonString(jsonObject, locations);
	}

	/**
	 * Extract the  original time of creation from the given metadata in JSON format
	 *
	 * @param jsonObject Extracted JSON formatted metadata (from @MetadataTools.getMetadataAsJSON())
	 * @return String value retrieved, or null if none were found
	 */
	public static String extractOriginalDateTime(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(EXIF_IFD_KEY, Arrays.asList(DATETIME_ORIGINAL_FIELD, CREATE_DATE_FIELD));
		locations.put(EXIF_KEY, List.of(DATETIME_ORIGINAL_FIELD));
		locations.put(XMP_XMP_KEY, List.of(CREATE_DATE_FIELD));
		locations.put(XMP_KEY, List.of(DATETIME_ORIGINAL_FIELD));
		locations.put(XMP_EXIF_KEY, List.of(DATETIME_ORIGINAL_FIELD));
		locations.put(COMPOSITE_KEY, Arrays.asList(DATETIME_ORIGINAL_FIELD, "DigitalCreationDateTime", "SubSecCreateDate", "SubSecDateTimeOriginal"));
		locations.put(XMP_PHOTOSHOP_KEY, List.of("DateCreated"));
		return extractJsonString(jsonObject, locations);
	}

	/**
	 * Extract the fraction of second of the original time of creation from the given metadata in JSON format
	 *
	 * @param jsonObject Extracted JSON formatted metadata (from @MetadataTools.getMetadataAsJSON())
	 * @return String value retrieved, or null if none were found
	 */

	public static int extractOriginalSecondFraction(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(EXIF_IFD_KEY, Arrays.asList("SubSecTimeOriginal", "SubSecTimeDigitized"));
		var fraction = extractJsonNumber(jsonObject, locations);

		if (fraction != null) {
			return fraction.intValue();
		}

		return 0;
	}

	/**
	 * Extract the document ID from the given metadata in JSON format
	 *
	 * @param jsonObject Extracted JSON formatted metadata (from @MetadataTools.getMetadataAsJSON())
	 * @return String value retrieved, or null if none were found
	 */
	public static String extractOriginalDocumentId(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(XMP_XMPMM_KEY, Arrays.asList("OriginalDocumentID", "DocumentID", "InstanceID", "DerivedFromOriginalDocumentID"));
		locations.put(XMP_KEY, Arrays.asList("OriginalDocumentID", "DocumentID", "InstanceID", "DerivedFromOriginalDocumentID"));
		return extractJsonString(jsonObject, locations);
	}

	/**
	 * Extract the list of subjects/keywords from the given metadata in JSON format
	 *
	 * @param jsonObject Extracted JSON formatted metadata (from @MetadataTools.getMetadataAsJSON())
	 * @return String value retrieved, or null if none were found
	 */
	public static List<String> extractSubjects(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(XMP_KEY, List.of("Subject"));
		locations.put(XMP_DC_KEY, List.of("Subject"));
		locations.put(XMP_LR_KEY, List.of("HierarchicalSubject", "WeightedFlatSubject"));
		locations.put(IPTC_KEY, List.of(IPTC_KEYWORD_FIELD));

		// We try first to extract an array of strings
		var subjectList = extractJsonArray(jsonObject, locations);

		// If the array is empty, we try to extract a single string
		if (subjectList.isEmpty()) {
			var subject = extractJsonString(jsonObject, locations);

			if (subject != null && !subject.isBlank()) {
				// clean the subject string so that it does not contain any leading or trailing spaces before adding it to the list
				subjectList.add(subject.trim());
			}
		}

		// Make sure the list contains only unique subjects
		subjectList = subjectList.stream()
								 .filter(Objects::nonNull)
								 .distinct()
								 .toList();

		return subjectList;
	}

	public static String extractRightsHolder(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(XMP_DC_KEY, List.of("Rights"));
		locations.put(IFD0_KEY, List.of("Copyright"));
		return extractJsonString(jsonObject, locations);
	}

	public static String extractRightsTerms(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(XMP_XMP_RIGHTS_KEY, List.of("UsageTerms"));
		return extractJsonString(jsonObject, locations);
	}

	public static String extractRightsUrl(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(XMP_XMP_RIGHTS_KEY, List.of("WebStatement"));
		return extractJsonString(jsonObject, locations);
	}

	public static String extractCreatorName(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(IFD0_KEY, List.of("Artist"));
		locations.put(XMP_DC_KEY, List.of("Creator"));
		return extractJsonString(jsonObject, locations);
	}

	public static String extractCreatorEmail(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(XMP_IPTC_CORE_KEY, List.of("CreatorWorkEmail"));
		return extractJsonString(jsonObject, locations);
	}

	public static String extractCreatorCountry(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(XMP_IPTC_CORE_KEY, List.of("CreatorCountry"));
		return extractJsonString(jsonObject, locations);
	}

	public static String extractCreatorUrl(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(XMP_IPTC_CORE_KEY, List.of("CreatorWorkURL"));
		return extractJsonString(jsonObject, locations);
	}

	public static String extractLabel(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(XMP_XMP_KEY, List.of("Label"));
		return extractJsonString(jsonObject, locations);
	}

	public static Instant extractGpsTime(JSONObject jsonObject) {
		var locations = new HashMap<String, List<String>>();
		locations.put(COMPOSITE_KEY, List.of("GPSDateTime"));
		var dateTimeStamp = extractJsonString(jsonObject, locations);

		if (dateTimeStamp == null) {
			locations.put(GPS_KEY, List.of("GPSTimeStamp"));
			var timeStamp = extractJsonString(jsonObject, locations);
			locations = new HashMap<>();
			locations.put(GPS_KEY, List.of("GPSDateStamp"));
			var dateStamp = extractJsonString(jsonObject, locations);
			// Combine date and time to a single string and parse it as a UTC datetime
			if (dateStamp != null
				&& !dateStamp.isBlank()
				&& timeStamp != null
				&& !timeStamp.isBlank()) {
				dateTimeStamp = dateStamp + " " + timeStamp + "Z";
			}
		}

		return dateTimeParser(dateTimeStamp);
	}

	public static GpsLocationEntity extractGpsData(JSONObject jsonObject) {
		var gpsLocationEntity = new GpsLocationEntity();

		var locations = new HashMap<String, List<String>>();
		// First attempt to fetch the GPSPosition
		locations.put(COMPOSITE_KEY, List.of("GPSLocation"));
		var gpsLocationString = extractJsonString(jsonObject, locations);

		if (gpsLocationString != null && !gpsLocationString.isBlank() && gpsLocationString.contains(",")) {
			// GPSPosition is in format "60 deg 10' 30.00\" N, 24 deg 58' 00.0\" E"
			var locationParts = gpsLocationString.split(",");

			if (locationParts.length == 2) {
				// Extract latitude (first part)
				var latitudePart   = locationParts[0].trim();
				var latitudeResult = extractCoordinateWithRef(latitudePart);

				// Extract longitude (second part)
				var longitudePart   = locationParts[1].trim();
				var longitudeResult = extractCoordinateWithRef(longitudePart);

				// Set the values to the entity
				if (latitudeResult != null) {
					gpsLocationEntity.setLatitudeRef(latitudeResult.getKey());
					gpsLocationEntity.setLatitude(latitudeResult.getValue());
				}

				if (longitudeResult != null) {
					gpsLocationEntity.setLongitudeRef(longitudeResult.getKey());
					gpsLocationEntity.setLongitude(longitudeResult.getValue());
				}
			}
		} else {
			// Try to get latitude and longitude from separate fields
			var latitudeResult  = extractSingleCoordinate(jsonObject, "GPSLatitude", "GPSLatitudeRef");
			var longitudeResult = extractSingleCoordinate(jsonObject, "GPSLongitude", "GPSLongitudeRef");

			if (latitudeResult != null) {
				gpsLocationEntity.setLatitudeRef(latitudeResult.getKey());
				gpsLocationEntity.setLatitude(latitudeResult.getValue());
			}

			if (longitudeResult != null) {
				gpsLocationEntity.setLongitudeRef(longitudeResult.getKey());
				gpsLocationEntity.setLongitude(longitudeResult.getValue());
			}
		}
		// GPS altitude, can be in form of "130 m" or "7 m Above Sea Level"
		locations = new HashMap<>();
		locations.put(GPS_KEY, List.of("GPSAltitude"));
		locations.put(COMPOSITE_KEY, List.of("GPSAltitude"));
		var altitudeString = extractJsonString(jsonObject, locations);

		if (altitudeString != null && !altitudeString.isBlank()) {
			var altitudeParts = altitudeString.split(" ");

			if (altitudeParts.length > 0) {
				try {
					var altitudeValue = Double.parseDouble(altitudeParts[0].trim());
					gpsLocationEntity.setAltitude(altitudeValue);
				} catch (NumberFormatException e) {
					log.error("Failed to parse GPS altitude: {}", altitudeString, e);
				}
			}
		}

		// GPS satellite count, seems to be given in a zero-filled format like "03"
		locations = new HashMap<>();
		locations.put(GPS_KEY, List.of("GPSSatellites"));

		try {
			var satelliteString = extractJsonString(jsonObject, locations);
			if (satelliteString != null && !satelliteString.isBlank()) {
				try {
					var satelliteCount = Integer.parseInt(satelliteString.trim());
					gpsLocationEntity.setSatelliteCount(satelliteCount);
				} catch (NumberFormatException e) {
					log.error("Failed to parse GPS satellite count: {}", satelliteString, e);
				}
			}
		} catch (JSONException e) {
			log.warn("Failed to parse GPS satellite count as string. Attempting to get number instead");
			var satelliteCount = extractJsonNumber(jsonObject, locations);

			if (satelliteCount != null) {
				gpsLocationEntity.setSatelliteCount(satelliteCount.intValue());
			}
		}

		// GPS direction, degrees with decimal, example 212.1
		locations = new HashMap<>();
		locations.put(GPS_KEY, List.of("GPSImgDirection"));
		var directionDouble = extractJsonNumber(jsonObject, locations);

		if (directionDouble != null) {
			try {
				var directionValue = directionDouble.doubleValue();
				gpsLocationEntity.setDirection(directionValue);
			} catch (NumberFormatException e) {
				log.error("Failed to parse GPS direction: {}", directionDouble, e);
			}
		}
		// Then we need to extract the location data, which is spread over multiple locations
		// Country, country code, e.g. "NL"
		locations = new HashMap<>();
		locations.put(XMP_IPTC_CORE_KEY, List.of("Country"));
		var countryCode = extractJsonString(jsonObject, locations);

		if (countryCode != null && !countryCode.isBlank()) {
			gpsLocationEntity.setCountry(countryCode);
		} else {
			// Country, full text localizer, e.g. "Alankomaat"
			locations = new HashMap<>();
			locations.put(XMP_PHOTOSHOP_KEY, List.of("Country"));
			var country = extractJsonString(jsonObject, locations);

			if (country != null && !country.isBlank()) {
				gpsLocationEntity.setCountry(country);
			}
		}
		// Province or state, e.g. "Uusimaa"
		locations = new HashMap<>();
		locations.put(XMP_PHOTOSHOP_KEY, List.of("State"));
		var state = extractJsonString(jsonObject, locations);

		if (state != null && !state.isBlank()) {
			gpsLocationEntity.setState(state);
		}
		// City, e.g. "Helsinki"
		locations = new HashMap<>();
		locations.put(XMP_PHOTOSHOP_KEY, List.of("City"));
		var city = extractJsonString(jsonObject, locations);
		if (city != null && !city.isBlank()) {
			gpsLocationEntity.setCity(city);
		}
		// Street, e.g. "Mannerheimintie". The location may also be just a number, e.g. "4" so we may get an exception here
		try {
			locations = new HashMap<>();
			locations.put(XMP_IPTC_CORE_KEY, List.of("Location"));
			var street = extractJsonString(jsonObject, locations);

			if (street != null && !street.isBlank()) {
				gpsLocationEntity.setStreet(street);
			}
		} catch (JSONException e) {
			log.warn("Failed to parse GPS location as string: {}", e.getMessage());
		}

		return gpsLocationEntity;
	}

	/**
	 * Extracts a coordinate value and its reference from separate metadata fields
	 *
	 * @param jsonObject    The metadata JSON object
	 * @param coordinateKey The key for the coordinate value
	 * @param refKey        The key for the coordinate reference
	 * @return A Map.Entry containing the reference character and coordinate value
	 */
	private static Map.Entry<Character, BigDecimal> extractSingleCoordinate(JSONObject jsonObject, String coordinateKey, String refKey) {
		// Get reference (N/S/E/W)
		var locations = new HashMap<String, List<String>>();
		locations.put(GPS_KEY, List.of(refKey));
		var       refString = extractJsonString(jsonObject, locations);
		Character ref       = refString != null && !refString.isBlank() ? refString.charAt(0) : null;

		// Get coordinate string
		locations = new HashMap<>();
		locations.put(GPS_KEY, List.of(coordinateKey));
		locations.put(COMPOSITE_KEY, List.of(coordinateKey));
		var coordinateString = extractJsonString(jsonObject, locations);

		// If no reference was found but coordinate has one at the end, extract it
		if (ref == null && coordinateString != null && !coordinateString.isBlank() && coordinateString.substring(coordinateString.length() - 1)
																									  .matches("[NSEW]")) {
			var result = extractCoordinateWithRef(coordinateString);
			ref = result.getKey();
			BigDecimal value = result.getValue();
			return entry(ref, value);
		}

		// Otherwise convert the coordinate with the provided reference
		BigDecimal value = convertGpsCoordinateStringToBigDecimal(coordinateString, ref);

		if (value == null) {
			log.warn("Failed to extract coordinate for key {} with reference {}", coordinateKey, ref);
			return null;
		}

		return entry(ref, value);
	}

	/**
	 * Extracts a coordinate and its reference from a single string
	 *
	 * @param coordinateString The coordinate string possibly containing a reference at the end
	 * @return A Map.Entry containing the reference character and coordinate value
	 */
	private static Map.Entry<Character, BigDecimal> extractCoordinateWithRef(String coordinateString) {
		if (coordinateString == null || coordinateString.isBlank()) {
			return null;
		}

		// Check if the last character is a cardinal point (N/S/E/W)
		char      lastChar = coordinateString.charAt(coordinateString.length() - 1);
		Character ref      = null;
		String    coordStr = coordinateString;

		if (lastChar == 'N' || lastChar == 'S' || lastChar == 'E' || lastChar == 'W') {
			ref      = lastChar;
			coordStr = coordinateString.substring(0, coordinateString.length() - 1)
									   .trim();
		}

		if (ref == null) {
			log.warn("No cardinal point found in coordinate string: {}", coordinateString);
			return null;
		}

		BigDecimal value = convertGpsCoordinateStringToBigDecimal(coordStr, ref);
		return entry(ref, value);
	}

	protected static BigDecimal convertGpsCoordinateStringToBigDecimal(String coordinateString, Character cardinalPoint) {
		if (cardinalPoint == null) {
			return null;
		}

		switch (cardinalPoint) {
			case 'N' -> {
				// North latitude, positive value
				if (coordinateString != null && !coordinateString.isBlank()) {
					try {
						return convertDmsToBigDecimal(coordinateString);
					} catch (Exception e) {
						log.error("Failed to parse GPS latitude: {} {}", coordinateString, cardinalPoint, e);
					}
				}
			}
			case 'S' -> {
				// South latitude, negative value
				if (coordinateString != null && !coordinateString.isBlank()) {
					try {
						return convertDmsToBigDecimal(coordinateString).negate();
					} catch (Exception e) {
						log.error("Failed to parse GPS latitude: {} {}", coordinateString, cardinalPoint, e);
					}
				}
			}
			case 'E' -> {
				// East longitude, positive value
				if (coordinateString != null && !coordinateString.isBlank()) {
					try {
						return convertDmsToBigDecimal(coordinateString);
					} catch (Exception e) {
						log.error("Failed to parse GPS longitude: {} {}", coordinateString, cardinalPoint, e);
					}
				}
			}
			case 'W' -> {
				// West longitude, negative value
				if (coordinateString != null && !coordinateString.isBlank()) {
					try {
						return convertDmsToBigDecimal(coordinateString).negate();
					} catch (Exception e) {
						log.error("Failed to parse GPS longitude: {} {}", coordinateString, cardinalPoint, e);
					}
				}
			}
			default -> log.warn("Unknown latitude reference: {}", cardinalPoint);

		}

		return null;
	}

	private static BigDecimal convertDmsToBigDecimal(String latitudeString) {
		// GPS coordinates are in DMS format, e.g. "60 deg 10' 30.00\""
		// We need to convert it to decimal format
		var dmsParts = latitudeString.split("[^0-9.+-]+");

		if (dmsParts.length < 3) {
			throw new IllegalArgumentException("Invalid DMS format: " + latitudeString);
		}

		try {
			BigDecimal degrees = new BigDecimal(dmsParts[0]);
			BigDecimal minutes = new BigDecimal(dmsParts[1]);
			BigDecimal seconds = new BigDecimal(dmsParts[2]);

			// Convert to decimal degrees: degrees + (minutes/60) + (seconds/3600)
			BigDecimal minutesFraction = minutes.divide(new BigDecimal("60"), GPS_DECIMAL_PRECISION, RoundingMode.HALF_UP);
			BigDecimal secondsFraction = seconds.divide(new BigDecimal("3600"), GPS_DECIMAL_PRECISION, RoundingMode.HALF_UP);

			return degrees.add(minutesFraction)
						  .add(secondsFraction)
						  .setScale(GPS_DECIMAL_PRECISION, RoundingMode.HALF_UP);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid number in DMS format: " + latitudeString, e);
		}
	}

	public static List<String> extractJsonArray(JSONObject jsonObject, Map<String, List<String>> locations) {
		for (Map.Entry<String, List<String>> location : locations.entrySet()) {
			for (String key : location.getValue()) {
				if (jsonObject.has(location.getKey()) && jsonObject.getJSONObject(location.getKey())
																   .has(key)) {
					Object targetObject = jsonObject.getJSONObject(location.getKey())
													.opt(key);

					if (targetObject instanceof JSONArray targetArray) {
						try {
							return targetArray.toList()
											  .stream()
											  .map(o -> Objects.toString(o, null))
											  .toList();
						} catch (Exception e) {
							log.error("Failed to convert JSON array for key {}", key, e);
						}
					} else {
						// Not an array: attempt to treat it as a single scalar value
						if (targetObject != null
							&& !(targetObject instanceof JSONObject)) { // Ignore nested objects
							String value = Objects.toString(targetObject, null);

							if (value != null && !value.isBlank()) {
								log.debug("Key {} under {} is not a JSON array, but a single value, returning a one-item array", key, location.getKey());
								return new ArrayList<>(List.of(value.trim()));
							}
						}
						var targetSimpleName = targetObject == null ? "null" : targetObject.getClass()
																						   .getSimpleName();
						log.warn("Key {} under {} is not a JSON array (type: {})", key, location.getKey(), targetSimpleName);
					}
				}
			}
		}

		return new ArrayList<>();
	}

	public static void copyMetadata(File sourceFile, File destinationFile) throws IOException {
		var command = new ArrayList<String>();

		command.add("exiftool");
		command.add("-overwrite_original_in_place");
		command.add("-TagsFromFile");
		command.add(sourceFile.getAbsolutePath());
		command.add("-all:all");
		command.add(destinationFile.getAbsolutePath());

		log.info("Running exiftool copy with command: {}", command);

		var processBuilder = new ProcessBuilder(command);
		var process        = processBuilder.start();

		if (process.isAlive()) {
			try {
				int exitCode = process.waitFor();
				if (exitCode != 0) {
					log.error("Exiftool copy process exited with code: {}", exitCode);
				} else {
					log.info("Exiftool copy process completed successfully.");
				}
			} catch (InterruptedException e) {
				Thread.currentThread()
					  .interrupt();
				log.error("Exiftool copy process was interrupted", e);
			}
		} else {
			log.error("Exiftool copy process failed to start.");
		}
	}

	public static JSONObject metadataToJsonObject(String metadata) {
		var jsonArray = new JSONArray(metadata);

		if (jsonArray.isEmpty()) {
			log.error("Failed to parse the metadata JSON from\n{}", metadata);
			return null;
		}

		return jsonArray.getJSONObject(0);
	}

	private static String extractJsonString(JSONObject jsonObject, Map<String, List<String>> locations) {
		for (Map.Entry<String, List<String>> location : locations.entrySet()) {
			for (String key : location.getValue()) {
				if (jsonObject.has(location.getKey()) && jsonObject.getJSONObject(location.getKey())
																   .has(key)) {
					return jsonObject.getJSONObject(location.getKey())
									 .getString(key);
				}
			}
		}

		return null;
	}

	private static Number extractJsonNumber(JSONObject jsonObject, Map<String, List<String>> locations) {
		for (Map.Entry<String, List<String>> location : locations.entrySet()) {
			for (String key : location.getValue()) {
				if (jsonObject.has(location.getKey()) && jsonObject.getJSONObject(location.getKey())
																   .has(key)) {

					Number number;
					try {
						number = jsonObject.getJSONObject(location.getKey())
										   .getNumber(key);
						return number;
					} catch (JSONException e) {
						log.warn("Failed to retrieve JSON number from location {}, trying to get it as String instead", key);
						var stringValue = jsonObject.getJSONObject(location.getKey())
													.getString(key);

						try {
							return Double.valueOf(stringValue);
						} catch (NumberFormatException ex) {
							log.error("Key {} exists but can not be parsed as number", key);
						}
					}
				}
			}
		}

		return null;
	}

	private static String runExifTool(File file, String... params) throws IOException {
		log.info("Running exiftool on file {} with tags: {}", file, String.join(", ", params));

		// Prepare command: exiftool -tag1 -tag2 ... file
		var command = new ArrayList<String>();
		command.add("exiftool");
		command.add("-j"); // Output in JSON so we get the correct field names
		Collections.addAll(command, params);
		command.add(file.getAbsolutePath());

		log.info("Running exiftool with command: {}", command);

		var processBuilder = new ProcessBuilder(command);
		var process        = processBuilder.start();

		var scanner = new Scanner(process.getInputStream()).useDelimiter("\\A");
		return scanner.hasNext() ? scanner.next() : "";
	}

	private static String getTagValue(String jsonOutput, String tag) {
		try {
			var mapper = new ObjectMapper();
			var root   = mapper.readTree(jsonOutput);

			if (root.isArray()
				&& !root.isEmpty()) {
				var first     = root.get(0);
				var valueNode = first.get(tag);
				return valueNode != null ? valueNode.asText() : "";
			}
		} catch (Exception e) {
			log.error("Failed to parse exiftool JSON output", e);
		}

		return "";
	}

	public static Instant dateTimeParser(String dateTimeString) {
		if (dateTimeString == null || dateTimeString.isBlank()) {
			return null;
		}

		var formatter = new DateTimeFormatterBuilder()
				// Date
				.appendPattern("yyyy:MM:dd HH:mm:ss")
				// Optional fractional seconds (from 1 to 9 digits)
				.optionalStart()
				.appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
				.optionalEnd()
				// Optional offset like +02:00
				.optionalStart()
				.appendOffset("+HH:mm", "Z")
				.optionalEnd()
				.toFormatter()
				.withZone(ZoneId.systemDefault());

		try {
			var timeStamp = formatter.parse(dateTimeString, Instant::from);
			log.info("Parsed date time string '{}' to Instant: {}", dateTimeString, timeStamp);
			return timeStamp;
		} catch (DateTimeParseException e) {
			log.error("Failed to parse date time string: {}", dateTimeString, e);
			return null;
		}
	}
}
