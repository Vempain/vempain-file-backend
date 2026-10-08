package fi.poltsi.vempain.file.service;

import feign.FeignException;
import fi.poltsi.vempain.admin.api.request.DataRequest;
import fi.poltsi.vempain.admin.api.response.DataResponse;
import fi.poltsi.vempain.file.api.TaskTypeEnum;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.MusicFileEntity;
import fi.poltsi.vempain.file.feign.VempainAdminDataClient;
import fi.poltsi.vempain.file.repository.files.ImageFileRepository;
import fi.poltsi.vempain.file.service.files.MusicFileService;
import fi.poltsi.vempain.file.task.TaskProgress;
import fi.poltsi.vempain.file.task.TaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service responsible for generating CSV datasets from the file database and
 * publishing them to the Vempain Admin service via the {@link VempainAdminDataClient}.
 *
 * <p>Generated CSV data is ephemeral — it is produced on demand at API call time
 * and is never persisted in the file database.</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DataService {

	/**
	 * Identifier used in the Admin data store for the music dataset.
	 */
	static final String MUSIC_IDENTIFIER      = "music_library";
	/**
	 * Identifier prefix used for GPS time-series datasets per directory.
	 */
	static final String GPS_IDENTIFIER_PREFIX = "gps_timeseries_";

	private static final DateTimeFormatter TIMESTAMP_FMT            =
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
			                 .withZone(ZoneOffset.UTC);
	private static final Pattern           ADMIN_IDENTIFIER_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

	private final MusicFileService    musicFileService;
	private final ImageFileRepository imageFileRepository;
	private final VempainAdminDataClient vempainAdminDataClient;
	private final LocationService locationService;
	private final TaskRunner taskRunner;

	// -----------------------------------------------------------------------
	// Music dataset
	// -----------------------------------------------------------------------

	/**
	 * Escapes a CSV field value: wraps in double-quotes if the value contains
	 * a comma, double-quote, or newline, and doubles any internal double-quotes.
	 */
	static String escapeCsv(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"%s\"".formatted(value.replace("\"", "\"\""));
		}
		return value;
	}

	/**
	 * Generates a CSV from all music files, then creates or updates the dataset
	 * in Vempain Admin and publishes it to the site database.
	 *
	 * @return the {@link DataResponse} returned by the Admin service after publishing
	 */
	public DataResponse generateAndPublishMusicDataset() {
		return publishMusic(loadMusicFilesOrThrow(), null);
	}

	/**
	 * Validates that music files exist and starts a background task that generates and publishes the music data set. The finished
	 * task carries the admin {@link DataResponse} as result.
	 */
	public TaskProgress publishMusicDatasetAsTask() {
		var musicFiles = loadMusicFilesOrThrow();
		return taskRunner.submitDurable(TaskTypeEnum.PUBLISH_MUSIC_DATA.name(), "Publish music data set", 3, java.util.Map.of(),
										progress -> publishMusic(musicFiles, progress));
	}

	/**
	 * Reloads source rows on the worker that claimed the durable command.
	 */
	public DataResponse publishMusicDatasetNow(TaskProgress progress) {
		return publishMusic(loadMusicFilesOrThrow(), progress);
	}

	private List<MusicFileEntity> loadMusicFilesOrThrow() {
		log.info("Collecting music files for the music dataset");
		var musicFiles = musicFileService.findAllOrdered();

		if (musicFiles.isEmpty()) {
			log.warn("No music files found in the database; skipping music dataset publication");
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No music files found in the database");
		}
		return musicFiles;
	}

	private DataResponse publishMusic(List<MusicFileEntity> musicFiles, TaskProgress progress) {
		checkpoint(progress);
		var csvData = buildMusicCsv(musicFiles);
		step(progress, "Generated CSV for " + musicFiles.size() + " music files");
		var request = buildMusicDataRequest(csvData);
		step(progress, "Prepared data set " + MUSIC_IDENTIFIER);
		checkpoint(progress);
		var response = createOrUpdate(request, progress);
		step(progress, "Published data set " + MUSIC_IDENTIFIER + " to the admin backend");
		return response;
	}

	private static void step(TaskProgress progress, String message) {
		if (progress != null) {
			progress.advance(message);
		}
	}

	private static void checkpoint(TaskProgress progress) {
		if (progress != null) {
			progress.checkpoint();
		}
	}

	/**
	 * Builds the CSV string for the music dataset.
	 */
	String buildMusicCsv(List<MusicFileEntity> musicFiles) {
		var sb = new StringBuilder();
		sb.append("artist,album_artist,album,year,track_number,track_total,track_name,genre,duration_seconds\n");

		for (var f : musicFiles) {
			sb.append(escapeCsv(f.getArtist()))
			  .append(',')
			  .append(escapeCsv(f.getAlbumArtist()))
			  .append(',')
			  .append(escapeCsv(f.getAlbum()))
			  .append(',')
			  .append(f.getYear() != null ? f.getYear() : "")
			  .append(',')
			  .append(f.getTrackNumber() != null ? f.getTrackNumber() : "")
			  .append(',')
			  .append(f.getTrackTotal() != null ? f.getTrackTotal() : "")
			  .append(',')
			  .append(escapeCsv(f.getTrackName()))
			  .append(',')
			  .append(escapeCsv(f.getGenre()))
			  .append(',')
			  .append(f.getDuration() != null ? f.getDuration()
			                                     .getSeconds() : "")
			  .append('\n');
		}
		return sb.toString();
	}

	// -----------------------------------------------------------------------
	// GPS time-series dataset
	// -----------------------------------------------------------------------

	private DataRequest buildMusicDataRequest(String csvData) {
		var request = new DataRequest();
		request.setIdentifier(MUSIC_IDENTIFIER);
		request.setType("tabulated");
		request.setDescription("Music library — all music files with artist, album, track, genre and duration");
		request.setColumnDefinitions("""
											 [{"name":"artist","type":"string"},
											  {"name":"album_artist","type":"string"},
											  {"name":"album","type":"string"},
											  {"name":"year","type":"integer"},
											  {"name":"track_number","type":"integer"},
											  {"name":"track_total","type":"integer"},
											  {"name":"track_name","type":"string"},
											  {"name":"genre","type":"string"},
											  {"name":"duration_seconds","type":"integer"}]
											 """);
		request.setCreateSql("""
									 CREATE TABLE website_data__%s (
									     id BIGSERIAL PRIMARY KEY,
									     artist VARCHAR(255),
									     album_artist VARCHAR(255),
									     album VARCHAR(255),
									     year INTEGER,
									     track_number INTEGER,
									     track_total INTEGER,
									     track_name VARCHAR(255),
									     genre VARCHAR(100),
									     duration_seconds INTEGER
									 )
									 """.formatted(MUSIC_IDENTIFIER));
		request.setFetchAllSql("""
									   SELECT id, artist, album_artist, album, year, track_number, track_total, track_name, genre, duration_seconds
									   FROM website_data__%s
									   ORDER BY artist, album, track_number
									   """.formatted(MUSIC_IDENTIFIER));
		request.setFetchSubsetSql("""
										  SELECT id, artist, album_artist, album, year, track_number, track_total, track_name, genre, duration_seconds
										  FROM website_data__%s
										  WHERE artist = :artist
										  ORDER BY album, track_number
										  """.formatted(MUSIC_IDENTIFIER));
		request.setGenerated(Instant.now());
		request.setCsvData(csvData);
		return request;
	}

	/**
	 * Generates a GPS time-series CSV from images with GPS data in the given
	 * directory path, then creates or updates the dataset in Vempain Admin and
	 * publishes it.
	 *
	 * @param directoryPath relative directory path (e.g. "/holidays/2024")
	 * @return the {@link DataResponse} returned by the Admin service after publishing
	 */
	public DataResponse generateAndPublishGpsTimeSeries(String directoryPath) {
		if (directoryPath == null || directoryPath.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Directory path must not be empty");
		}

		// Normalise: strip leading/trailing slash characters, then re-add one leading slash.
		// Uses simple character-by-character scanning to avoid ReDoS risk with complex regex.
		int start = 0;
		int end   = directoryPath.length();
		while (start < end && directoryPath.charAt(start) == '/') {
			start++;
		}
		while (end > start && directoryPath.charAt(end - 1) == '/') {
			end--;
		}
		var normPath = "/%s".formatted(directoryPath.substring(start, end));
		log.info("Generating GPS time-series dataset for directory: {}", normPath);

		var images = imageFileRepository.findByFilePathWithGpsOrderedByTime(normPath)
		                                .stream()
		                                .filter(image -> !locationService.isGuardedLocation(image.getGpsLocation()))
		                                .toList();

		if (images.isEmpty()) {
			log.warn("No GPS-tagged images found in directory: {}", normPath);
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
			                                  """
													  No GPS-tagged images found in directory: %s
													  """.formatted(normPath)
			                                             .strip());
		}

		var identifier = buildGpsIdentifier(normPath);
		var csvData    = buildGpsCsv(images);
		var request    = buildGpsDataRequest(identifier, normPath, csvData);
		return createOrUpdate(request, null);
	}

	/**
	 * Generates a GPS time-series CSV from images with GPS data in the given
	 * file group, then creates or updates the dataset in Vempain Admin and publishes it.
	 *
	 * @param fileGroupId    ID of the file group containing images with GPS data
	 * @param timeSeriesName the identifier/name of the time-series dataset
	 * @return the {@link DataResponse} returned by the Admin service after publishing
	 */
	public DataResponse generateAndPublishGpsTimeSeriesByFileGroup(Long fileGroupId, String timeSeriesName) {
		var identifier = validateGpsIdentifier(fileGroupId, timeSeriesName);
		return publishGpsTimeSeries(identifier, fileGroupId, loadGpsImagesOrThrow(fileGroupId), null);
	}

	/**
	 * Validates the request and the presence of publishable GPS images, then starts a background task that generates and publishes
	 * the GPS time-series data set. The finished task carries the admin {@link DataResponse} as result.
	 */
	public TaskProgress publishGpsTimeSeriesByFileGroupAsTask(Long fileGroupId, String timeSeriesName) {
		var identifier = validateGpsIdentifier(fileGroupId, timeSeriesName);
		var images     = loadGpsImagesOrThrow(fileGroupId);
		return taskRunner.submitDurable(TaskTypeEnum.PUBLISH_GPS_TIME_SERIES.name(), "Publish GPS time series " + identifier, 3,
										java.util.Map.of("file_group_id", fileGroupId, "time_series_name", timeSeriesName),
								 progress -> publishGpsTimeSeries(identifier, fileGroupId, images, progress));
	}

	public DataResponse publishGpsTimeSeriesNow(Long fileGroupId, String timeSeriesName, TaskProgress progress) {
		var identifier = validateGpsIdentifier(fileGroupId, timeSeriesName);
		return publishGpsTimeSeries(identifier, fileGroupId, loadGpsImagesOrThrow(fileGroupId), progress);
	}

	private String validateGpsIdentifier(Long fileGroupId, String timeSeriesName) {
		if (fileGroupId == null || fileGroupId <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File group ID must be a positive number");
		}

		if (timeSeriesName == null || timeSeriesName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Time series name must not be empty");
		}

		var normalizedIdentifier = normalizeAdminIdentifier(timeSeriesName);
		if (!normalizedIdentifier.equals(timeSeriesName)) {
			log.info("Normalized requested time-series name '{}' to Admin identifier '{}'", timeSeriesName, normalizedIdentifier);
		}
		return normalizedIdentifier;
	}

	private List<ImageFileEntity> loadGpsImagesOrThrow(Long fileGroupId) {
		var images = imageFileRepository.findByFileGroupIdWithGpsOrderedByTime(fileGroupId)
		                                .stream()
		                                .filter(image -> !locationService.isGuardedLocation(image.getGpsLocation()))
		                                .toList();

		if (images.isEmpty()) {
			log.warn("No publishable GPS-tagged images found in file group: {}", fileGroupId);
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No publishable GPS-tagged images found in file group");
		}
		return images;
	}

	private DataResponse publishGpsTimeSeries(String identifier, Long fileGroupId, List<ImageFileEntity> images, TaskProgress progress) {
		log.info("Generating GPS time-series dataset for file group: {} with name: {}", fileGroupId, identifier);
		checkpoint(progress);
		var csvData = buildGpsCsv(images);
		step(progress, "Generated CSV for " + images.size() + " GPS-tagged images");
		var request = buildGpsDataRequest(identifier, "file group: " + fileGroupId, csvData);
		step(progress, "Prepared data set " + identifier);
		checkpoint(progress);
		var response = createOrUpdate(request, progress);
		step(progress, "Published data set " + identifier + " to the admin backend");
		return response;
	}

	/**
	 * Derives a valid data-store identifier from the directory path.
	 * Identifier rules: starts with a-z, only a-z0-9_ allowed.
	 *
	 * <p>Uses only simple, non-backtracking regex patterns to avoid ReDoS risk.</p>
	 */
	String buildGpsIdentifier(String path) {
		// Lower-case and replace every non-alphanumeric/underscore/slash character with '_'
		var lower = path.toLowerCase();
		var sb    = new StringBuilder(lower.length());
		for (char c : lower.toCharArray()) {
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
				sb.append(c);
			} else {
				sb.append('_');
			}
		}
		// Collapse consecutive underscores (simple linear scan, no regex)
		var collapsed = new StringBuilder();
		boolean prevUnderscore = false;
		for (int i = 0; i < sb.length(); i++) {
			char c = sb.charAt(i);
			if (c == '_') {
				if (!prevUnderscore) {
					collapsed.append(c);
				}
				prevUnderscore = true;
			} else {
				collapsed.append(c);
				prevUnderscore = false;
			}
		}
		// Strip leading characters until we find an [a-z] start
		var str = collapsed.toString();
		int alphaStart = 0;
		while (alphaStart < str.length() && !(str.charAt(alphaStart) >= 'a' && str.charAt(alphaStart) <= 'z')) {
			alphaStart++;
		}
		str = alphaStart < str.length() ? str.substring(alphaStart) : "dir";
		// Strip trailing underscore
		if (!str.isEmpty() && str.charAt(str.length() - 1) == '_') {
			str = str.substring(0, str.length() - 1);
		}
		var identifier = "%s%s".formatted(GPS_IDENTIFIER_PREFIX, str);
		// Ensure total length is reasonable
		if (identifier.length() > 60) {
			identifier = identifier.substring(0, 60);
		}
		return identifier;
	}

	/**
	 * Builds the CSV string for the GPS time-series dataset.
	 */
	String buildGpsCsv(List<ImageFileEntity> images) {
		var sb = new StringBuilder();
		sb.append("timestamp,latitude,latitude_ref,longitude,longitude_ref,altitude,filename\n");

		for (var img : images) {
			var gps = img.getGpsLocation();
			var timestamp = img.getGpsTimestamp() != null
			                ? TIMESTAMP_FMT.format(img.getGpsTimestamp())
			                : (img.getOriginalDatetime() != null
			                   ? TIMESTAMP_FMT.format(img.getOriginalDatetime())
			                   : "");

			sb.append(escapeCsv(timestamp))
			  .append(',')
			  .append(gps.getLatitude() != null ? gps.getLatitude()
			                                         .toPlainString() : "")
			  .append(',')
			  .append(gps.getLatitudeRef() != null ? gps.getLatitudeRef() : "")
			  .append(',')
			  .append(gps.getLongitude() != null ? gps.getLongitude()
			                                          .toPlainString() : "")
			  .append(',')
			  .append(gps.getLongitudeRef() != null ? gps.getLongitudeRef() : "")
			  .append(',')
			  .append(gps.getAltitude() != null ? gps.getAltitude() : "")
			  .append(',')
			  .append(escapeCsv(img.getFilename()))
			  .append('\n');
		}
		return sb.toString();
	}

	// -----------------------------------------------------------------------
	// Admin API interaction
	// -----------------------------------------------------------------------

	private DataRequest buildGpsDataRequest(String identifier, String path, String csvData) {
		var tableBase = "website_data__%s".formatted(identifier);
		var request   = new DataRequest();
		request.setIdentifier(identifier);
		request.setType("time_series");
		request.setDescription("""
									   GPS time-series for directory: %s
									   """.formatted(path)
		                                  .strip());
		request.setColumnDefinitions("""
											 [{"name":"timestamp","type":"string"},
											  {"name":"latitude","type":"decimal"},
											  {"name":"latitude_ref","type":"string"},
											  {"name":"longitude","type":"decimal"},
											  {"name":"longitude_ref","type":"string"},
											  {"name":"altitude","type":"decimal"},
											  {"name":"filename","type":"string"}]
											 """);
		request.setCreateSql("""
									 CREATE TABLE %s (
									     id BIGSERIAL PRIMARY KEY,
									     timestamp TIMESTAMP,
									     latitude DECIMAL(15,5),
									     latitude_ref CHAR(1),
									     longitude DECIMAL(15,5),
									     longitude_ref CHAR(1),
									     altitude DOUBLE PRECISION,
									     filename VARCHAR(255)
									 )
									 """.formatted(tableBase));
		request.setFetchAllSql("""
									   SELECT id, timestamp, latitude, latitude_ref, longitude, longitude_ref, altitude, filename
									   FROM %s
									   ORDER BY timestamp ASC
									   """.formatted(tableBase));
		request.setFetchSubsetSql("""
										  SELECT id, timestamp, latitude, latitude_ref, longitude, longitude_ref, altitude, filename
										  FROM %s
										  WHERE timestamp BETWEEN :from AND :to
										  ORDER BY timestamp ASC
										  """.formatted(tableBase));
		request.setGenerated(Instant.now());
		request.setCsvData(csvData);
		return request;
	}

	/**
	 * Creates or replaces the data set in the admin backend. When running as a task, the undo is registered first: a created
	 * data set is deleted again, a replaced one gets its previous content back.
	 */
	private DataResponse createOrUpdate(DataRequest request, TaskProgress progress) {
		DataResponse existing;

		log.debug("Prepared Admin DataRequest identifier='{}', type='{}', csv_header='{}', csv_length={} chars",
		          request.getIdentifier(), request.getType(), csvHeaderPreview(request.getCsvData()),
		          request.getCsvData() != null ? request.getCsvData()
		                                                .length() : 0);

		try {
			log.debug("Checking if the identifier {} already exists on Vempain Admin", request.getIdentifier());
			var existingResponse = vempainAdminDataClient.getDataSetByIdentifier(request.getIdentifier());
			existing = existingResponse == null ? null : existingResponse.getBody();
			if (existing == null) {
				existing = new DataResponse();
				existing.setIdentifier(request.getIdentifier());
			}
		} catch (FeignException.NotFound e) {
			existing = null;
		}

		if (existing != null) {
			var previous = toRequest(existing);
			if (progress != null) {
				progress.registerCompensation("Restore the previous content of data set " + request.getIdentifier(),
											  () -> vempainAdminDataClient.updateDataSet(previous));
			}
			try {
				log.debug("Updating existing dataset: {}", request.getIdentifier());
				return vempainAdminDataClient.updateDataSet(request)
				                             .getBody();
			} catch (FeignException e) {
				log.error("Failed to create/update dataset '{}': status={}, msg={}, body={}",
				          request.getIdentifier(), e.status(), e.getMessage(), e.contentUTF8(), e);
				throw mapAdminException(e, "create/update", request.getIdentifier());
			}
		}

		log.debug("identifier {} did not exist on Vempain Admin, creating it", request.getIdentifier());
		var created = create(request);
		if (progress != null) {
			var identifier = request.getIdentifier();
			progress.registerCompensation("Delete data set " + identifier + " from the admin backend",
										  () -> vempainAdminDataClient.deleteDataSet(identifier));
		}
		return created;
	}

	private static DataRequest toRequest(DataResponse response) {
		var request = new DataRequest();
		request.setIdentifier(response.getIdentifier());
		request.setType(response.getType());
		request.setDescription(response.getDescription());
		request.setColumnDefinitions(response.getColumnDefinitions());
		request.setCreateSql(response.getCreateSql());
		request.setFetchAllSql(response.getFetchAllSql());
		request.setFetchSubsetSql(response.getFetchSubsetSql());
		request.setGenerated(response.getGenerated());
		request.setCsvData(response.getCsvData());
		return request;
	}

	private DataResponse create(DataRequest request) {
		try {
			var createResponse = vempainAdminDataClient.createDataSet(request);

			if (createResponse == null || !createResponse.getStatusCode()
			                                             .is2xxSuccessful()) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
				                                  "Failed to create dataset in Admin service");
			}
			log.debug("Created dataset: {}", request.getIdentifier());
			return createResponse.getBody();
		} catch (FeignException e) {
			log.error("Failed to create dataset '{}': status={}, msg={}, body={}",
			          request.getIdentifier(), e.status(), e.getMessage(), e.contentUTF8());
			throw mapAdminException(e, "create", request.getIdentifier());
		}
	}

	private String normalizeAdminIdentifier(String candidate) {
		var lower = candidate.trim()
		                     .toLowerCase();
		var sb = new StringBuilder(lower.length());
		for (char c : lower.toCharArray()) {
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
				sb.append(c);
			} else {
				sb.append('_');
			}
		}

		// Collapse duplicate underscores and trim the edges.
		var     collapsed          = new StringBuilder(sb.length());
		boolean previousUnderscore = false;
		for (int i = 0; i < sb.length(); i++) {
			char c = sb.charAt(i);
			if (c == '_') {
				if (!previousUnderscore) {
					collapsed.append(c);
				}
				previousUnderscore = true;
			} else {
				collapsed.append(c);
				previousUnderscore = false;
			}
		}
		while (!collapsed.isEmpty() && collapsed.charAt(0) == '_') {
			collapsed.deleteCharAt(0);
		}
		while (!collapsed.isEmpty() && collapsed.charAt(collapsed.length() - 1) == '_') {
			collapsed.deleteCharAt(collapsed.length() - 1);
		}

		if (collapsed.isEmpty()) {
			collapsed.append("gps_timeseries");
		}
		if (!(collapsed.charAt(0) >= 'a' && collapsed.charAt(0) <= 'z')) {
			collapsed.insert(0, "gps_timeseries_");
		}

		var normalized = collapsed.toString();
		if (normalized.length() > 60) {
			normalized = normalized.substring(0, 60);
		}

		if (!ADMIN_IDENTIFIER_PATTERN.matcher(normalized)
		                             .matches()) {
			log.error("Failed to normalize Admin identifier from '{}' to a valid value. Produced '{}'", candidate, normalized);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time series name");
		}
		return normalized;
	}

	private String csvHeaderPreview(String csvData) {
		if (csvData == null || csvData.isBlank()) {
			return "";
		}
		var newlineIndex = csvData.indexOf('\n');
		return newlineIndex >= 0 ? csvData.substring(0, newlineIndex)
		                                  .trim() : csvData.trim();
	}

	// -----------------------------------------------------------------------
	// Utility
	// -----------------------------------------------------------------------

	private ResponseStatusException mapAdminException(FeignException exception, String action, String identifier) {
		log.warn("Admin service failed to {} dataset '{}' with status {}", action, identifier, exception.status());
		var reason = "Admin service failed to %s dataset".formatted(action);
		return switch (exception.status()) {
			case 400 -> new ResponseStatusException(HttpStatus.BAD_REQUEST, reason, exception);
			case 404 -> new ResponseStatusException(HttpStatus.NOT_FOUND, reason, exception);
			case 409 -> new ResponseStatusException(HttpStatus.CONFLICT, reason, exception);
			default -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, reason, exception);
		};
	}
}
