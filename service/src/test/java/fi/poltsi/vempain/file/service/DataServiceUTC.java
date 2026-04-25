package fi.poltsi.vempain.file.service;

import feign.FeignException;
import fi.poltsi.vempain.admin.api.request.DataRequest;
import fi.poltsi.vempain.admin.api.response.DataResponse;
import fi.poltsi.vempain.file.entity.GpsLocationEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.MusicFileEntity;
import fi.poltsi.vempain.file.feign.VempainAdminDataClient;
import fi.poltsi.vempain.file.repository.files.ImageFileRepository;
import fi.poltsi.vempain.file.service.files.MusicFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataServiceUTC {

	@Mock
	private MusicFileService        musicFileService;
	@Mock
	private ImageFileRepository     imageFileRepository;
	@Mock
	private VempainAdminDataClient  vempainAdminDataClient;

	private DataService dataService;

	@BeforeEach
	void setUp() {
		dataService = new DataService(musicFileService, imageFileRepository, vempainAdminDataClient);
	}

	// -----------------------------------------------------------------------
	// buildMusicCsv
	// -----------------------------------------------------------------------

	@Test
	void buildMusicCsv_basicRow() {
		var music = new MusicFileEntity();
		music.setArtist("The Beatles");
		music.setAlbumArtist("The Beatles");
		music.setAlbum("Abbey Road");
		music.setYear(1969);
		music.setTrackNumber(1);
		music.setTrackTotal(17);
		music.setTrackName("Come Together");
		music.setGenre("Rock");
		music.setDuration(Duration.ofSeconds(259));

		var csv = dataService.buildMusicCsv(List.of(music));

		assertThat(csv).contains("artist,album_artist,album,year,track_number,track_total,track_name,genre,duration_seconds");
		assertThat(csv).contains("The Beatles,The Beatles,Abbey Road,1969,1,17,Come Together,Rock,259");
	}

	@Test
	void buildMusicCsv_nullFields_producesEmptyColumns() {
		var music = new MusicFileEntity();
		// All fields null / default

		var csv = dataService.buildMusicCsv(List.of(music));

		// Should still have header + data row with empty values
		assertThat(csv).startsWith("artist,album");
		var lines = csv.split("\n");
		assertThat(lines).hasSize(2); // header + one data row
	}

	@Test
	void buildMusicCsv_specialCharactersEscaped() {
		var music = new MusicFileEntity();
		music.setArtist("Guns N' Roses");
		music.setAlbum("\"Appetite for Destruction\"");
		music.setTrackName("Welcome to the Jungle");

		var csv = dataService.buildMusicCsv(List.of(music));

		assertThat(csv).contains("Guns N' Roses");
		// The album has double-quotes so it should be wrapped
		assertThat(csv).contains("\"\"\"Appetite for Destruction\"\"\"");
	}

	// -----------------------------------------------------------------------
	// buildGpsIdentifier
	// -----------------------------------------------------------------------

	@Test
	void buildGpsIdentifier_simpleDir() {
		var id = dataService.buildGpsIdentifier("/holidays/2024");
		assertThat(id).startsWith(DataService.GPS_IDENTIFIER_PREFIX);
		assertThat(id).matches("^[a-z][a-z0-9_]*$");
	}

	@Test
	void buildGpsIdentifier_rootDir() {
		var id = dataService.buildGpsIdentifier("/");
		assertThat(id).startsWith(DataService.GPS_IDENTIFIER_PREFIX);
		assertThat(id).matches("^[a-z][a-z0-9_]*$");
	}

	@Test
	void buildGpsIdentifier_specialCharsReplaced() {
		var id = dataService.buildGpsIdentifier("/my photos/2024 trip");
		assertThat(id).doesNotContain(" ");
		assertThat(id).matches("^[a-z][a-z0-9_]*$");
	}

	// -----------------------------------------------------------------------
	// buildGpsCsv
	// -----------------------------------------------------------------------

	@Test
	void buildGpsCsv_withGpsData() {
		var gps = GpsLocationEntity.builder()
								   .latitude(new BigDecimal("60.12345"))
								   .latitudeRef('N')
								   .longitude(new BigDecimal("24.98765"))
								   .longitudeRef('E')
								   .altitude(130.0)
								   .build();

		var image = new ImageFileEntity();
		image.setFilename("photo.jpg");
		image.setGpsTimestamp(Instant.parse("2024-06-01T12:00:00Z"));
		image.setGpsLocation(gps);

		var csv = dataService.buildGpsCsv(List.of(image));

		assertThat(csv).contains("timestamp,latitude");
		assertThat(csv).contains("2024-06-01T12:00:00Z");
		assertThat(csv).contains("60.12345");
		assertThat(csv).contains("N");
		assertThat(csv).contains("photo.jpg");
	}

	// -----------------------------------------------------------------------
	// generateAndPublishMusicDataset — no music files
	// -----------------------------------------------------------------------

	@Test
	void generateAndPublishMusicDataset_noMusicFiles_throws404() {
		when(musicFileService.findAllOrdered()).thenReturn(List.of());

		assertThrows(ResponseStatusException.class,
					 () -> dataService.generateAndPublishMusicDataset());
	}

	// -----------------------------------------------------------------------
	// generateAndPublishMusicDataset — happy path
	// -----------------------------------------------------------------------

	@Test
	void generateAndPublishMusicDataset_publishesCorrectIdentifier() {
		var music = new MusicFileEntity();
		music.setArtist("Miles Davis");
		music.setAlbumArtist("Miles Davis");
		music.setAlbum("Kind of Blue");
		music.setYear(1959);
		music.setTrackName("So What");
		music.setTrackNumber(1);
		music.setTrackTotal(5);
		music.setGenre("Jazz");
		music.setDuration(Duration.ofSeconds(565));

		when(musicFileService.findAllOrdered()).thenReturn(List.of(music));
		when(vempainAdminDataClient.getDataSetByIdentifier(DataService.MUSIC_IDENTIFIER))
				.thenThrow(mock(FeignException.NotFound.class));

		var dataResponse = new DataResponse();
		dataResponse.setIdentifier(DataService.MUSIC_IDENTIFIER);
		when(vempainAdminDataClient.createDataSet(any(DataRequest.class)))
				.thenReturn(ResponseEntity.ok(dataResponse));

		var result = dataService.generateAndPublishMusicDataset();

		assertThat(result).isNotNull();
		assertThat(result.getIdentifier()).isEqualTo(DataService.MUSIC_IDENTIFIER);

		// Verify create was called with correct request fields
		var captor = ArgumentCaptor.forClass(DataRequest.class);
		verify(vempainAdminDataClient).createDataSet(captor.capture());
		var req = captor.getValue();
		assertThat(req.getIdentifier()).isEqualTo(DataService.MUSIC_IDENTIFIER);
		assertThat(req.getCreateSql()).startsWith("CREATE TABLE");
		assertThat(req.getCreateSql()).contains("album_artist");
		assertThat(req.getCreateSql()).contains("year");
		assertThat(req.getCreateSql()).contains("track_total");
		assertThat(req.getCsvData()).contains("Miles Davis");
		assertThat(req.getCsvData()).contains("1959");
		assertThat(req.getCsvData()).contains("album_artist");
		assertThat(req.getCsvData()).contains("track_total");
		verify(vempainAdminDataClient).getDataSetByIdentifier(DataService.MUSIC_IDENTIFIER);
		verify(vempainAdminDataClient, never()).updateDataSet(any(DataRequest.class));
		verify(vempainAdminDataClient, never()).publishDataSet(any(String.class));
	}

	// -----------------------------------------------------------------------
	// generateAndPublishGpsTimeSeries — no GPS images
	// -----------------------------------------------------------------------

	@Test
	void generateAndPublishGpsTimeSeries_noGpsImages_throws404() {
		when(imageFileRepository.findByFilePathWithGpsOrderedByTime(any())).thenReturn(List.of());

		assertThrows(ResponseStatusException.class,
					 () -> dataService.generateAndPublishGpsTimeSeries("/some/path"));
	}

	// -----------------------------------------------------------------------
	// generateAndPublishGpsTimeSeries — blank path
	// -----------------------------------------------------------------------

	@Test
	void generateAndPublishGpsTimeSeries_blankPath_throws400() {
		assertThrows(ResponseStatusException.class,
					 () -> dataService.generateAndPublishGpsTimeSeries("  "));
	}

	// -----------------------------------------------------------------------
	// escapeCsv
	// -----------------------------------------------------------------------

	@Test
	void escapeCsv_nullReturnsEmpty() {
		assertThat(DataService.escapeCsv(null)).isEmpty();
	}

	@Test
	void escapeCsv_noSpecialChars_unchanged() {
		assertThat(DataService.escapeCsv("hello world")).isEqualTo("hello world");
	}

	@Test
	void escapeCsv_commaWrapped() {
		assertThat(DataService.escapeCsv("a,b")).isEqualTo("\"a,b\"");
	}

	@Test
	void escapeCsv_quoteDoubled() {
		assertThat(DataService.escapeCsv("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
	}
}
