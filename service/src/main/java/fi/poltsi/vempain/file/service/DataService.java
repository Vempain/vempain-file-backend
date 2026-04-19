package fi.poltsi.vempain.file.service;

import feign.FeignException;
import fi.poltsi.vempain.admin.api.request.DataRequest;
import fi.poltsi.vempain.admin.api.response.DataResponse;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.MusicFileEntity;
import fi.poltsi.vempain.file.feign.VempainAdminDataClient;
import fi.poltsi.vempain.file.repository.files.ImageFileRepository;
import fi.poltsi.vempain.file.service.files.MusicFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

	/** Identifier used in the Admin data store for the music dataset. */
	static final String MUSIC_IDENTIFIER = "music_library";
	/** Identifier prefix used for GPS time-series datasets per directory. */
	static final String GPS_IDENTIFIER_PREFIX = "gps_timeseries_";

	private static final DateTimeFormatter TIMESTAMP_FMT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

	private final MusicFileService      musicFileService;
	private final ImageFileRepository   imageFileRepository;
	private final VempainAdminDataClient vempainAdminDataClient;

	// -----------------------------------------------------------------------
	// Music dataset
	// -----------------------------------------------------------------------

	/**
	 * Generates a CSV from all music files, then creates or updates the dataset
	 * in Vempain Admin and publishes it to the site database.
	 *
	 * @return the {@link DataResponse} returned by the Admin service after publishing
	 */
	public DataResponse generateAndPublishMusicDataset() {
		log.info("Generating music dataset CSV");
		var musicFiles = musicFileService.findAllOrdered();

		if (musicFiles.isEmpty()) {
			log.warn("No music files found in the database; skipping music dataset publication");
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No music files found in the database");
		}

		var csvData = buildMusicCsv(musicFiles);
		var request = buildMusicDataRequest(csvData);
		return createOrUpdate(request);
	}

	/**
	 * Builds the CSV string for the music dataset.
	 */
	String buildMusicCsv(List<MusicFileEntity> musicFiles) {
		var sb = new StringBuilder();
		sb.append("artist,album,track_number,track_name,genre,duration_seconds\n");

		for (var f : musicFiles) {
			sb.append(escapeCsv(f.getArtist()))
			  .append(',')
			  .append(escapeCsv(f.getAlbum()))
			  .append(',')
			  .append(f.getTrackNumber() != null ? f.getTrackNumber() : "")
			  .append(',')
			  .append(escapeCsv(f.getTrackName()))
			  .append(',')
			  .append(escapeCsv(f.getGenre()))
			  .append(',')
			  .append(f.getDuration() != null ? f.getDuration().getSeconds() : "")
			  .append('\n');
		}
		return sb.toString();
	}

	private DataRequest buildMusicDataRequest(String csvData) {
		var request = new DataRequest();
		request.setIdentifier(MUSIC_IDENTIFIER);
		request.setType("tabulated");
		request.setDescription("Music library — all music files with artist, album, track, genre and duration");
		request.setColumnDefinitions(
				"[{\"name\":\"artist\",\"type\":\"string\"},"
				+ "{\"name\":\"album\",\"type\":\"string\"},"
				+ "{\"name\":\"track_number\",\"type\":\"integer\"},"
				+ "{\"name\":\"track_name\",\"type\":\"string\"},"
				+ "{\"name\":\"genre\",\"type\":\"string\"},"
				+ "{\"name\":\"duration_seconds\",\"type\":\"integer\"}]"
		);
		request.setCreateSql(
				"CREATE TABLE website_data__" + MUSIC_IDENTIFIER
				+ " (id BIGSERIAL PRIMARY KEY, artist VARCHAR(255), album VARCHAR(255),"
				+ " track_number INTEGER, track_name VARCHAR(255), genre VARCHAR(100),"
				+ " duration_seconds INTEGER)"
		);
		request.setFetchAllSql(
				"SELECT id, artist, album, track_number, track_name, genre, duration_seconds"
				+ " FROM website_data__" + MUSIC_IDENTIFIER
				+ " ORDER BY artist, album, track_number"
		);
		request.setFetchSubsetSql(
				"SELECT id, artist, album, track_number, track_name, genre, duration_seconds"
				+ " FROM website_data__" + MUSIC_IDENTIFIER
				+ " WHERE artist = :artist ORDER BY album, track_number"
		);
		request.setGenerated(Instant.now());
		request.setCsvData(csvData);
		return request;
	}

	// -----------------------------------------------------------------------
	// GPS time-series dataset
	// -----------------------------------------------------------------------

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

		// Normalise: ensure leading slash, no trailing slash
		var normPath = "/" + directoryPath.replaceAll("^/+|/+$", "");
		log.info("Generating GPS time-series dataset for directory: {}", normPath);

		var images = imageFileRepository.findByFilePathWithGpsOrderedByTime(normPath);

		if (images.isEmpty()) {
			log.warn("No GPS-tagged images found in directory: {}", normPath);
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
											  "No GPS-tagged images found in directory: " + normPath);
		}

		var identifier = buildGpsIdentifier(normPath);
		var csvData    = buildGpsCsv(images);
		var request    = buildGpsDataRequest(identifier, normPath, csvData);
		return createOrUpdate(request);
	}

	/**
	 * Derives a valid data-store identifier from the directory path.
	 * Identifier rules: starts with a-z, only a-z0-9_ allowed.
	 */
	String buildGpsIdentifier(String path) {
		var cleaned = path.toLowerCase()
						  .replaceAll("^[^a-z]+", "")   // strip leading non-alpha
						  .replaceAll("[^a-z0-9_/]", "_") // replace invalid chars
						  .replace('/', '_')              // slashes → underscore
						  .replaceAll("_+", "_")          // collapse multiple underscores
						  .replaceAll("_$", "");          // strip trailing underscore
		var identifier = GPS_IDENTIFIER_PREFIX + cleaned;
		// Ensure total length is reasonable (Admin identifier max is not specified but keep it sane)
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
			var gps       = img.getGpsLocation();
			var timestamp = img.getGpsTimestamp() != null
							? TIMESTAMP_FMT.format(img.getGpsTimestamp())
							: (img.getOriginalDatetime() != null
							   ? TIMESTAMP_FMT.format(img.getOriginalDatetime())
							   : "");

			sb.append(escapeCsv(timestamp))
			  .append(',')
			  .append(gps.getLatitude() != null ? gps.getLatitude().toPlainString() : "")
			  .append(',')
			  .append(gps.getLatitudeRef() != null ? gps.getLatitudeRef() : "")
			  .append(',')
			  .append(gps.getLongitude() != null ? gps.getLongitude().toPlainString() : "")
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

	private DataRequest buildGpsDataRequest(String identifier, String path, String csvData) {
		var tableBase = "website_data__" + identifier;
		var request   = new DataRequest();
		request.setIdentifier(identifier);
		request.setType("time_series");
		request.setDescription("GPS time-series for directory: " + path);
		request.setColumnDefinitions(
				"[{\"name\":\"timestamp\",\"type\":\"string\"},"
				+ "{\"name\":\"latitude\",\"type\":\"decimal\"},"
				+ "{\"name\":\"latitude_ref\",\"type\":\"string\"},"
				+ "{\"name\":\"longitude\",\"type\":\"decimal\"},"
				+ "{\"name\":\"longitude_ref\",\"type\":\"string\"},"
				+ "{\"name\":\"altitude\",\"type\":\"decimal\"},"
				+ "{\"name\":\"filename\",\"type\":\"string\"}]"
		);
		request.setCreateSql(
				"CREATE TABLE " + tableBase
				+ " (id BIGSERIAL PRIMARY KEY, timestamp TIMESTAMP,"
				+ " latitude DECIMAL(15,5), latitude_ref CHAR(1),"
				+ " longitude DECIMAL(15,5), longitude_ref CHAR(1),"
				+ " altitude DOUBLE PRECISION, filename VARCHAR(255))"
		);
		request.setFetchAllSql(
				"SELECT id, timestamp, latitude, latitude_ref, longitude, longitude_ref, altitude, filename"
				+ " FROM " + tableBase + " ORDER BY timestamp ASC"
		);
		request.setFetchSubsetSql(
				"SELECT id, timestamp, latitude, latitude_ref, longitude, longitude_ref, altitude, filename"
				+ " FROM " + tableBase + " WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp ASC"
		);
		request.setGenerated(Instant.now());
		request.setCsvData(csvData);
		return request;
	}

	// -----------------------------------------------------------------------
	// Admin API interaction
	// -----------------------------------------------------------------------

	/**
	 * Attempts to update an existing dataset; if not found (404), creates a new one.
	 * After creation/update, publishes the dataset to the site database.
	 */
	private DataResponse createOrUpdate(DataRequest request) {
		DataResponse dataResponse;
		try {
			// Try to update first (the Admin side returns 404 if not found)
			var updateResponse = vempainAdminDataClient.updateDataSet(request);
			if (updateResponse != null && updateResponse.getStatusCode().is2xxSuccessful()) {
				log.debug("Updated existing dataset: {}", request.getIdentifier());
				dataResponse = updateResponse.getBody();
			} else {
				dataResponse = create(request);
			}
		} catch (FeignException.NotFound e) {
			log.debug("Dataset not found, creating: {}", request.getIdentifier());
			dataResponse = create(request);
		} catch (FeignException e) {
			log.error("Failed to create/update dataset '{}': status={}, msg={}",
					  request.getIdentifier(), e.status(), e.getMessage());
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
											  "Failed to create/update dataset in Admin service: " + e.getMessage(), e);
		}

		return publish(request.getIdentifier());
	}

	private DataResponse create(DataRequest request) {
		try {
			var createResponse = vempainAdminDataClient.createDataSet(request);
			if (createResponse == null || !createResponse.getStatusCode().is2xxSuccessful()) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
												  "Failed to create dataset in Admin service");
			}
			log.debug("Created dataset: {}", request.getIdentifier());
			return createResponse.getBody();
		} catch (FeignException e) {
			log.error("Failed to create dataset '{}': status={}, msg={}",
					  request.getIdentifier(), e.status(), e.getMessage());
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
											  "Failed to create dataset in Admin service: " + e.getMessage(), e);
		}
	}

	private DataResponse publish(String identifier) {
		try {
			var publishResponse = vempainAdminDataClient.publishDataSet(identifier);
			if (publishResponse == null || !publishResponse.getStatusCode().is2xxSuccessful()) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
												  "Failed to publish dataset in Admin service");
			}
			log.info("Published dataset: {}", identifier);
			return publishResponse.getBody();
		} catch (FeignException e) {
			log.error("Failed to publish dataset '{}': status={}, msg={}",
					  identifier, e.status(), e.getMessage());
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
											  "Failed to publish dataset in Admin service: " + e.getMessage(), e);
		}
	}

	// -----------------------------------------------------------------------
	// Utility
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
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}
}
