package fi.poltsi.vempain.file.tools;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

@Slf4j
public class MetadataTool {
	private static final String COMPOSITE_KEY      = "Composite";
	private static final String EXIF_KEY           = "EXIF";
	private static final String EXIF_IFD_KEY       = "ExifIFD";
	private static final String IPTC_KEY           = "IPTC";
	private static final String IFD0_KEY           = "IFD0";
	private static final String FILE_KEY           = "File";
	private static final String SUBIFD_1_KEY       = "SUBIFD1";
	private static final String SUBIFD_2_KEY       = "SUBIFD2";
	private static final String SUBIFD_3_KEY       = "SUBIFD3";
	private static final String SUBIFD_KEY         = "SUBIFD";
	private static final String XMP_DC_KEY         = "XMP-dc";
	private static final String XMP_IPTC_CORE_KEY  = "XMP-iptcCore";
	private static final String XMP_KEY            = "XMP";
	private static final String XMP_EXIF_KEY       = "XMP-exif";
	private static final String XMP_LR_KEY         = "XMP-lr";
	private static final String XMP_PHOTOSHOP_KEY  = "XMP-photoshop";
	private static final String XMP_XMPMM_KEY      = "XMP-xmpMM";
	private static final String XMP_XMP_KEY        = "XMP-xmp";
	private static final String XMP_XMP_RIGHTS_KEY = "XMP-xmpRights";
	private static final String SYSTEM_KEY         = "System";

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

	public static Dimension extractImageResolution(JSONObject jsonObject) throws IOException {
		Map<String, List<String>> locations = new HashMap<>();
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
	 * @throws IOException If an error occurs during extraction
	 */
	public static int extractImageColorDepth(JSONObject jsonObject) throws IOException {
		Map<String, List<String>> locations = new HashMap<>();
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

	public static int extractImageDpi(JSONObject jsonObject) throws IOException {
		Map<String, List<String>> locations = new HashMap<>();
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
		return Double.parseDouble(getTagValue(output, "Duration"));
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
	public static String getDescriptionFromJson(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
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
		Map<String, List<String>> locations = new HashMap<>();
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
	public static String getOriginalDateTimeFromJson(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
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

	public static int getOriginalSecondFraction(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
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
	public static String getOriginalDocumentId(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
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
	public static List<String> getSubjects(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
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

	public static String getRightsHolder(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
		locations.put(XMP_DC_KEY, List.of("Rights"));
		locations.put(IFD0_KEY, List.of("Copyright"));
		return extractJsonString(jsonObject, locations);
	}

	public static String getRightsTerms(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
		locations.put(XMP_XMP_RIGHTS_KEY, List.of("UsageTerms"));
		return extractJsonString(jsonObject, locations);
	}

	public static String getRightsUrl(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
		locations.put(XMP_XMP_RIGHTS_KEY, List.of("WebStatement"));
		return extractJsonString(jsonObject, locations);
	}

	public static String getCreatorName(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
		locations.put(IFD0_KEY, List.of("Artist"));
		locations.put(XMP_DC_KEY, List.of("Creator"));
		return extractJsonString(jsonObject, locations);
	}

	public static String getCreatorEmail(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
		locations.put(XMP_IPTC_CORE_KEY, List.of("CreatorWorkEmail"));
		return extractJsonString(jsonObject, locations);
	}

	public static String getCreatorCountry(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
		locations.put(XMP_IPTC_CORE_KEY, List.of("CreatorCountry"));
		return extractJsonString(jsonObject, locations);
	}

	public static String getCreatorUrl(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
		locations.put(XMP_IPTC_CORE_KEY, List.of("CreatorWorkURL"));
		return extractJsonString(jsonObject, locations);
	}

	public static String getLabel(JSONObject jsonObject) {
		Map<String, List<String>> locations = new HashMap<>();
		locations.put(XMP_XMP_KEY, List.of("Label"));
		return extractJsonString(jsonObject, locations);
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
							return Integer.parseInt(stringValue);
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
}
