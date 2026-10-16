package fi.poltsi.vempain.file.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import fi.poltsi.vempain.admin.api.request.DataRequest;
import fi.poltsi.vempain.admin.api.response.DataResponse;
import fi.poltsi.vempain.common.api.TaskStatusEnum;
import fi.poltsi.vempain.common.task.TaskProgress;
import fi.poltsi.vempain.common.task.TaskProgressStore;
import fi.poltsi.vempain.common.task.TaskRunner;
import fi.poltsi.vempain.file.entity.GpsLocationEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.MusicFileEntity;
import fi.poltsi.vempain.file.feign.VempainAdminDataClient;
import fi.poltsi.vempain.file.repository.files.ImageFileRepository;
import fi.poltsi.vempain.file.service.files.MusicFileService;

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
    private MusicFileService musicFileService;
    @Mock
    private ImageFileRepository imageFileRepository;
    @Mock
    private VempainAdminDataClient vempainAdminDataClient;
    @Mock
    private LocationService locationService;

    private final TaskRunner taskRunner = new TaskRunner(new TaskProgressStore(), Runnable::run);

    private DataService dataService;

    @BeforeEach
    void setUp() {
        dataService = new DataService(musicFileService, imageFileRepository, vempainAdminDataClient, locationService, taskRunner);
    }

    // -----------------------------------------------------------------------
    // buildMusicCsv
    // -----------------------------------------------------------------------

    @Test
    void buildMusicCsvBasicRow() {
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
    void buildMusicCsvNullFieldsProducesEmptyColumns() {
        var music = new MusicFileEntity();
        // All fields null / default

        var csv = dataService.buildMusicCsv(List.of(music));

        // Should still have header + data row with empty values
        assertThat(csv).startsWith("artist,album");
        var lines = csv.split("\n");
        assertThat(lines).hasSize(2); // header + one data row
    }

    @Test
    void buildMusicCsvSpecialCharactersEscaped() {
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
    void buildGpsIdentifierSimpleDir() {
        var id = dataService.buildGpsIdentifier("/holidays/2024");
        assertThat(id).startsWith(DataService.GPS_IDENTIFIER_PREFIX);
        assertThat(id).matches("^[a-z][a-z0-9_]*$");
    }

    @Test
    void buildGpsIdentifierRootDir() {
        var id = dataService.buildGpsIdentifier("/");
        assertThat(id).startsWith(DataService.GPS_IDENTIFIER_PREFIX);
        assertThat(id).matches("^[a-z][a-z0-9_]*$");
    }

    @Test
    void buildGpsIdentifierSpecialCharsReplaced() {
        var id = dataService.buildGpsIdentifier("/my photos/2024 trip");
        assertThat(id).doesNotContain(" ");
        assertThat(id).matches("^[a-z][a-z0-9_]*$");
    }

    // -----------------------------------------------------------------------
    // buildGpsCsv
    // -----------------------------------------------------------------------

    @Test
    void buildGpsCsvWithGpsData() {
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
    void generateAndPublishMusicDatasetNoMusicFilesThrows404() {
        when(musicFileService.findAllOrdered()).thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () -> dataService.generateAndPublishMusicDataset());
    }

    // -----------------------------------------------------------------------
    // generateAndPublishMusicDataset — happy path
    // -----------------------------------------------------------------------

    @Test
    void generateAndPublishMusicDatasetPublishesCorrectIdentifier() {
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
        when(vempainAdminDataClient.getDataSetByIdentifier(DataService.MUSIC_IDENTIFIER)).thenThrow(mock(FeignException.NotFound.class));

        var dataResponse = new DataResponse();
        dataResponse.setIdentifier(DataService.MUSIC_IDENTIFIER);
        when(vempainAdminDataClient.createDataSet(any(DataRequest.class))).thenReturn(ResponseEntity.ok(dataResponse));

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
    void generateAndPublishGpsTimeSeriesNoGpsImagesThrows404() {
        when(imageFileRepository.findByFilePathWithGpsOrderedByTime(any())).thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () -> dataService.generateAndPublishGpsTimeSeries("/some/path"));
    }

    // -----------------------------------------------------------------------
    // generateAndPublishGpsTimeSeries — blank path
    // -----------------------------------------------------------------------

    @Test
    void generateAndPublishGpsTimeSeriesBlankPathThrows400() {
        assertThrows(ResponseStatusException.class, () -> dataService.generateAndPublishGpsTimeSeries("  "));
    }

    // -----------------------------------------------------------------------
    // generateAndPublishGpsTimeSeriesByFileGroup — no GPS images
    // -----------------------------------------------------------------------

    @Test
    void generateAndPublishGpsTimeSeriesByFileGroupNoGpsImagesThrows404() {
        when(imageFileRepository.findByFileGroupIdWithGpsOrderedByTime(any())).thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () -> dataService.generateAndPublishGpsTimeSeriesByFileGroup(1L, "test-series"));
    }

    // -----------------------------------------------------------------------
    // generateAndPublishGpsTimeSeriesByFileGroup — invalid file_groupId
    // -----------------------------------------------------------------------

    @Test
    void generateAndPublishGpsTimeSeriesByFileGroupNullFileGroupIdThrows400() {
        assertThrows(ResponseStatusException.class, () -> dataService.generateAndPublishGpsTimeSeriesByFileGroup(null, "test-series"));
    }

    @Test
    void generateAndPublishGpsTimeSeriesByFileGroupZeroFileGroupIdThrows400() {
        assertThrows(ResponseStatusException.class, () -> dataService.generateAndPublishGpsTimeSeriesByFileGroup(0L, "test-series"));
    }

    @Test
    void generateAndPublishGpsTimeSeriesByFileGroupNegativeFileGroupIdThrows400() {
        assertThrows(ResponseStatusException.class, () -> dataService.generateAndPublishGpsTimeSeriesByFileGroup(-1L, "test-series"));
    }

    // -----------------------------------------------------------------------
    // generateAndPublishGpsTimeSeriesByFileGroup — blank timeSeriesName
    // -----------------------------------------------------------------------

    @Test
    void generateAndPublishGpsTimeSeriesByFileGroupBlankTimeSeriesNameThrows400() {
        assertThrows(ResponseStatusException.class, () -> dataService.generateAndPublishGpsTimeSeriesByFileGroup(1L, "  "));
    }

    @Test
    void generateAndPublishGpsTimeSeriesByFileGroupNullTimeSeriesNameThrows400() {
        assertThrows(ResponseStatusException.class, () -> dataService.generateAndPublishGpsTimeSeriesByFileGroup(1L, null));
    }

    // -----------------------------------------------------------------------
    // generateAndPublishGpsTimeSeriesByFileGroup — happy path
    // -----------------------------------------------------------------------

    @Test
    void generateAndPublishGpsTimeSeriesByFileGroupPublishesCorrectIdentifier() {
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

        when(imageFileRepository.findByFileGroupIdWithGpsOrderedByTime(42L)).thenReturn(List.of(image));
        when(vempainAdminDataClient.getDataSetByIdentifier("holidays_2024")).thenThrow(mock(FeignException.NotFound.class));

        var dataResponse = new DataResponse();
        dataResponse.setIdentifier("holidays_2024");
        when(vempainAdminDataClient.createDataSet(any(DataRequest.class))).thenReturn(ResponseEntity.ok(dataResponse));

        var result = dataService.generateAndPublishGpsTimeSeriesByFileGroup(42L, "holidays_2024");

        assertThat(result).isNotNull();
        assertThat(result.getIdentifier()).isEqualTo("holidays_2024");

        // Verify create was called with correct request fields
        var captor = ArgumentCaptor.forClass(DataRequest.class);
        verify(vempainAdminDataClient).createDataSet(captor.capture());
        var req = captor.getValue();
        assertThat(req.getIdentifier()).isEqualTo("holidays_2024");
        assertThat(req.getType()).isEqualTo("time_series");
        assertThat(req.getCreateSql()).startsWith("CREATE TABLE");
        assertThat(req.getCreateSql()).contains("timestamp");
        assertThat(req.getCreateSql()).contains("latitude");
        assertThat(req.getCsvData()).contains("2024-06-01T12:00:00Z");
        assertThat(req.getCsvData()).contains("60.12345");
        assertThat(req.getCsvData()).contains("photo.jpg");
        verify(vempainAdminDataClient).getDataSetByIdentifier("holidays_2024");
        verify(vempainAdminDataClient, never()).updateDataSet(any(DataRequest.class));
    }

    @Test
    void generateAndPublishGpsTimeSeriesByFileGroupExcludesGuardedLocations() {
        var gps = GpsLocationEntity.builder()
            .latitude(new BigDecimal("60.12345"))
            .longitude(new BigDecimal("24.98765"))
            .build();
        var image = new ImageFileEntity();
        image.setFilename("private.jpg");
        image.setGpsLocation(gps);

        when(imageFileRepository.findByFileGroupIdWithGpsOrderedByTime(43L)).thenReturn(List.of(image));
        when(locationService.isGuardedLocation(gps)).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> dataService.generateAndPublishGpsTimeSeriesByFileGroup(43L, "private-series"));
        verify(vempainAdminDataClient, never()).createDataSet(any(DataRequest.class));
    }

    @Test
    void generateAndPublishGpsTimeSeriesByFileGroupHyphenatedNameNormalizesIdentifier() {
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

        when(imageFileRepository.findByFileGroupIdWithGpsOrderedByTime(473L)).thenReturn(List.of(image));
        when(vempainAdminDataClient.getDataSetByIdentifier("matkailu_etiopia_2016")).thenThrow(mock(FeignException.NotFound.class));

        var dataResponse = new DataResponse();
        dataResponse.setIdentifier("matkailu_etiopia_2016");
        when(vempainAdminDataClient.createDataSet(any(DataRequest.class))).thenReturn(ResponseEntity.ok(dataResponse));

        var result = dataService.generateAndPublishGpsTimeSeriesByFileGroup(473L, "matkailu-etiopia-2016");

        assertThat(result).isNotNull();
        assertThat(result.getIdentifier()).isEqualTo("matkailu_etiopia_2016");
        verify(vempainAdminDataClient).getDataSetByIdentifier("matkailu_etiopia_2016");

        var captor = ArgumentCaptor.forClass(DataRequest.class);
        verify(vempainAdminDataClient).createDataSet(captor.capture());
        assertThat(captor.getValue()
            .getIdentifier()).isEqualTo("matkailu_etiopia_2016");
    }

    @Test
    void generateAndPublishGpsTimeSeriesByFileGroupDigitPrefixNameGetsSafePrefix() {
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

        when(imageFileRepository.findByFileGroupIdWithGpsOrderedByTime(474L)).thenReturn(List.of(image));
        when(vempainAdminDataClient.getDataSetByIdentifier("gps_timeseries_2016_trip")).thenThrow(mock(FeignException.NotFound.class));

        var dataResponse = new DataResponse();
        dataResponse.setIdentifier("gps_timeseries_2016_trip");
        when(vempainAdminDataClient.createDataSet(any(DataRequest.class))).thenReturn(ResponseEntity.ok(dataResponse));

        var result = dataService.generateAndPublishGpsTimeSeriesByFileGroup(474L, "2016-trip");

        assertThat(result).isNotNull();
        assertThat(result.getIdentifier()).isEqualTo("gps_timeseries_2016_trip");
        verify(vempainAdminDataClient).getDataSetByIdentifier("gps_timeseries_2016_trip");

        var captor = ArgumentCaptor.forClass(DataRequest.class);
        verify(vempainAdminDataClient).createDataSet(captor.capture());
        assertThat(captor.getValue()
            .getIdentifier()).isEqualTo("gps_timeseries_2016_trip");
    }

    // -----------------------------------------------------------------------
    // escapeCsv
    // -----------------------------------------------------------------------

    @Test
    void escapeCsvNullReturnsEmpty() {
        assertThat(DataService.escapeCsv(null)).isEmpty();
    }

    @Test
    void escapeCsvNoSpecialCharsUnchanged() {
        assertThat(DataService.escapeCsv("hello world")).isEqualTo("hello world");
    }

    @Test
    void escapeCsvCommaWrapped() {
        assertThat(DataService.escapeCsv("a,b")).isEqualTo("\"a,b\"");
    }

    @Test
    void escapeCsvQuoteDoubled() {
        assertThat(DataService.escapeCsv("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    }

    // -----------------------------------------------------------------------
    // background task variants
    // -----------------------------------------------------------------------

    @Test
    void publishMusicDatasetAsTaskNoMusicFilesThrows404BeforeStartingATask() {
        when(musicFileService.findAllOrdered()).thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () -> dataService.publishMusicDatasetAsTask());
        verify(vempainAdminDataClient, never()).createDataSet(any(DataRequest.class));
    }

    @Test
    void publishMusicDatasetAsTaskPublishesInThreeStepsAndCarriesTheDataResponse() {
        var music = new MusicFileEntity();
        music.setArtist("Miles Davis");
        when(musicFileService.findAllOrdered()).thenReturn(List.of(music));
        when(vempainAdminDataClient.getDataSetByIdentifier(DataService.MUSIC_IDENTIFIER)).thenThrow(mock(FeignException.NotFound.class));
        var dataResponse = new DataResponse();
        dataResponse.setIdentifier(DataService.MUSIC_IDENTIFIER);
        when(vempainAdminDataClient.createDataSet(any(DataRequest.class))).thenReturn(ResponseEntity.ok(dataResponse));

        var task = dataService.publishMusicDatasetAsTask();

        assertThat(task.getType()).isEqualTo("PUBLISH_MUSIC_DATA");
        assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
        assertThat(task.getCompletedSteps()
            .get()).isEqualTo(3);
        assertThat(task.getResult()).isSameAs(dataResponse);
    }

    @Test
    void publishGpsTimeSeriesByFileGroupAsTaskValidatesSynchronously() {
        assertThrows(ResponseStatusException.class, () -> dataService.publishGpsTimeSeriesByFileGroupAsTask(0L, "name"));
        assertThrows(ResponseStatusException.class, () -> dataService.publishGpsTimeSeriesByFileGroupAsTask(1L, " "));
        when(imageFileRepository.findByFileGroupIdWithGpsOrderedByTime(1L)).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> dataService.publishGpsTimeSeriesByFileGroupAsTask(1L, "Trip"));
        verify(vempainAdminDataClient, never()).createDataSet(any(DataRequest.class));
    }

    @Test
    void publishGpsTimeSeriesByFileGroupAsTaskFailsTheTaskWhenTheAdminBackendRejectsTheDataSet() {
        var location = new GpsLocationEntity();
        location.setLatitude(new BigDecimal("60.12345"));
        location.setLatitudeRef('N');
        location.setLongitude(new BigDecimal("24.93545"));
        location.setLongitudeRef('E');
        var image = new ImageFileEntity();
        image.setFilename("photo.jpg");
        image.setGpsLocation(location);
        when(imageFileRepository.findByFileGroupIdWithGpsOrderedByTime(5L)).thenReturn(List.of(image));
        when(locationService.isGuardedLocation(location)).thenReturn(false);
        when(vempainAdminDataClient.getDataSetByIdentifier("summer_trip")).thenThrow(new IllegalStateException("admin backend down"));

        var task = dataService.publishGpsTimeSeriesByFileGroupAsTask(5L, "Summer Trip");

        assertThat(task.getType()).isEqualTo("PUBLISH_GPS_TIME_SERIES");
        assertThat(task.getTitle()).isEqualTo("Publish GPS time series summer_trip");
        assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.FAILED);
        assertThat(task.getErrorMessage()).isEqualTo("admin backend down");
        assertThat(task.getCompletedSteps()
            .get()).isEqualTo(2);
    }

    @Test
    void publishMusicDatasetAsTaskRestoresThePreviousDataSetWhenTheUpdateFails() {
        var music = new MusicFileEntity();
        music.setArtist("Miles Davis");
        when(musicFileService.findAllOrdered()).thenReturn(List.of(music));
        var previous = new DataResponse();
        previous.setIdentifier(DataService.MUSIC_IDENTIFIER);
        previous.setType("tabulated");
        previous.setCsvData("artist\nOld Artist\n");
        when(vempainAdminDataClient.getDataSetByIdentifier(DataService.MUSIC_IDENTIFIER)).thenReturn(ResponseEntity.ok(previous));
        when(vempainAdminDataClient.updateDataSet(any(DataRequest.class))).thenThrow(new IllegalStateException("admin backend down"))
            .thenReturn(ResponseEntity.ok(previous));

        var task = dataService.publishMusicDatasetAsTask();

        assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.FAILED);
        assertThat(task.getRevertedSteps()
            .get()).isEqualTo(1);
        var captor = ArgumentCaptor.forClass(DataRequest.class);
        verify(vempainAdminDataClient, org.mockito.Mockito.times(2)).updateDataSet(captor.capture());
        assertThat(captor.getAllValues()
            .get(0)
            .getCsvData()).contains("Miles Davis");
        assertThat(captor.getAllValues()
            .get(1)
            .getCsvData()).isEqualTo("artist\nOld Artist\n");
    }

    @Test
    void publishGpsTimeSeriesByFileGroupAsTaskRegistersDeletionOfACreatedDataSet() {
        var location = new GpsLocationEntity();
        location.setLatitude(new BigDecimal("60.12345"));
        location.setLatitudeRef('N');
        location.setLongitude(new BigDecimal("24.93545"));
        location.setLongitudeRef('E');
        var image = new ImageFileEntity();
        image.setFilename("photo.jpg");
        image.setGpsLocation(location);
        when(imageFileRepository.findByFileGroupIdWithGpsOrderedByTime(5L)).thenReturn(List.of(image));
        when(locationService.isGuardedLocation(location)).thenReturn(false);
        when(vempainAdminDataClient.getDataSetByIdentifier("trip")).thenThrow(mock(FeignException.NotFound.class));
        var created = new DataResponse();
        created.setIdentifier("trip");
        when(vempainAdminDataClient.createDataSet(any(DataRequest.class))).thenReturn(ResponseEntity.ok(created));

        var task = dataService.publishGpsTimeSeriesByFileGroupAsTask(5L, "trip");

        assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
        assertThat(task.getResult()).isSameAs(created);
        verify(vempainAdminDataClient, never()).deleteDataSet(any());
    }

    private static FeignException feignFailure(int status) {
        return FeignException.errorStatus("admin", feign.Response.builder()
            .status(status)
            .reason("error")
            .request(Request.create(Request.HttpMethod.POST, "http://admin", Map.of(), null, new RequestTemplate()))
            .headers(Map.of())
            .build());
    }

    private MusicFileEntity oneMusicFile() {
        var music = new MusicFileEntity();
        music.setArtist("Miles Davis");
        when(musicFileService.findAllOrdered()).thenReturn(List.of(music));
        return music;
    }

    private ImageFileEntity oneGpsImage(Long fileGroupId, String directory) {
        var location = new GpsLocationEntity();
        location.setLatitude(new BigDecimal("60.12345"));
        location.setLatitudeRef('N');
        location.setLongitude(new BigDecimal("24.93545"));
        location.setLongitudeRef('E');
        var image = new ImageFileEntity();
        image.setFilename("photo.jpg");
        image.setGpsLocation(location);
        image.setOriginalDatetime(Instant.parse("2024-05-06T07:08:09Z"));
        if (fileGroupId != null) {
            when(imageFileRepository.findByFileGroupIdWithGpsOrderedByTime(fileGroupId)).thenReturn(List.of(image));
        }
        if (directory != null) {
            when(imageFileRepository.findByFilePathWithGpsOrderedByTime(directory)).thenReturn(List.of(image));
        }
        when(locationService.isGuardedLocation(location)).thenReturn(false);
        return image;
    }

    @Test
    void publishMusicDatasetAsTaskUpdatesAnExistingDataSetInThreeSteps() {
        oneMusicFile();
        var updated = new DataResponse();
        updated.setIdentifier(DataService.MUSIC_IDENTIFIER);
        // an empty answer still means the data set exists on the admin side
        when(vempainAdminDataClient.getDataSetByIdentifier(DataService.MUSIC_IDENTIFIER)).thenReturn(ResponseEntity.ok(null));
        when(vempainAdminDataClient.updateDataSet(any(DataRequest.class))).thenReturn(ResponseEntity.ok(updated));

        var task = dataService.publishMusicDatasetAsTask();

        assertThat(task.getType()).isEqualTo("PUBLISH_MUSIC_DATA");
        assertThat(task.getStatus()).isEqualTo(TaskStatusEnum.COMPLETED);
        assertThat(task.getCompletedSteps()
            .get()).isEqualTo(3);
        assertThat(task.getResult()).isSameAs(updated);
        verify(vempainAdminDataClient, never()).createDataSet(any(DataRequest.class));

        var reloaded = dataService.publishMusicDatasetNow(TaskProgress.unmanaged("t", "PUBLISH_MUSIC_DATA", "title", 1L, 3, "{}"));
        assertThat(reloaded).isSameAs(updated);
        verify(musicFileService, org.mockito.Mockito.times(2)).findAllOrdered();
    }

    @Test
    void adminFailuresAreMappedToHttpStatuses() {
        oneMusicFile();
        var existing = new DataResponse();
        existing.setIdentifier(DataService.MUSIC_IDENTIFIER);
        when(vempainAdminDataClient.getDataSetByIdentifier(DataService.MUSIC_IDENTIFIER)).thenReturn(ResponseEntity.ok(existing));
        when(vempainAdminDataClient.updateDataSet(any(DataRequest.class))).thenThrow(feignFailure(409));
        var conflict = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class, () -> dataService.generateAndPublishMusicDataset());
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        when(vempainAdminDataClient.getDataSetByIdentifier(DataService.MUSIC_IDENTIFIER)).thenThrow(mock(FeignException.NotFound.class));
        when(vempainAdminDataClient.createDataSet(any(DataRequest.class))).thenThrow(feignFailure(400), feignFailure(404), feignFailure(500))
            .thenReturn(ResponseEntity.status(500)
                .build());
        for (var expected : List.of(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)) {
            var failure = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class, () -> dataService.generateAndPublishMusicDataset());
            assertThat(failure.getStatusCode()).isEqualTo(expected);
        }
    }

    @Test
    void gpsTimeSeriesByDirectoryNormalisesThePathAndPublishes() {
        oneGpsImage(null, "/holidays/2024");
        when(vempainAdminDataClient.getDataSetByIdentifier("gps_timeseries_holidays_2024")).thenThrow(mock(FeignException.NotFound.class));
        var created = new DataResponse();
        created.setIdentifier("gps_timeseries_holidays_2024");
        when(vempainAdminDataClient.createDataSet(any(DataRequest.class))).thenReturn(ResponseEntity.ok(created));

        var response = dataService.generateAndPublishGpsTimeSeries("//holidays/2024/");

        assertThat(response).isSameAs(created);
        var captor = ArgumentCaptor.forClass(DataRequest.class);
        verify(vempainAdminDataClient).createDataSet(captor.capture());
        assertThat(captor.getValue()
            .getIdentifier()).isEqualTo("gps_timeseries_holidays_2024");
        assertThat(captor.getValue()
            .getType()).isEqualTo("time_series");
        // the original date is used when there is no GPS timestamp
        assertThat(captor.getValue()
            .getCsvData()).contains("2024-05-06T07:08:09Z,60.12345,N,24.93545,E,,photo.jpg");
    }

    @Test
    void buildGpsIdentifierStripsTrailingUnderscoresAndCapsTheLength() {
        assertThat(dataService.buildGpsIdentifier("/a/")).isEqualTo("gps_timeseries_a");
        var identifier = dataService.buildGpsIdentifier("/" + "abcdefghij".repeat(8));
        assertThat(identifier).hasSize(60)
            .startsWith("gps_timeseries_abcdefghij");
    }

    @Test
    void publishGpsTimeSeriesNowReloadsTheImagesOfTheGroup() {
        oneGpsImage(5L, null);
        when(vempainAdminDataClient.getDataSetByIdentifier("trip")).thenThrow(mock(FeignException.NotFound.class));
        var created = new DataResponse();
        created.setIdentifier("trip");
        when(vempainAdminDataClient.createDataSet(any(DataRequest.class))).thenReturn(ResponseEntity.ok(created));
        var progress = TaskProgress.unmanaged("t", "PUBLISH_GPS_TIME_SERIES", "title", 1L, 3, "{}");

        assertThat(dataService.publishGpsTimeSeriesNow(5L, "trip", progress)).isSameAs(created);
        assertThat(progress.getCompletedSteps()
            .get()).isEqualTo(3);
    }

    @Test
    void timeSeriesNamesAreNormalisedToAdminIdentifiers() {
        oneGpsImage(5L, null);
        when(vempainAdminDataClient.getDataSetByIdentifier(any())).thenThrow(mock(FeignException.NotFound.class));
        when(vempainAdminDataClient.createDataSet(any(DataRequest.class))).thenAnswer(invocation -> {
            var response = new DataResponse();
            response.setIdentifier(invocation.getArgument(0, DataRequest.class)
                .getIdentifier());
            return ResponseEntity.ok(response);
        });

        assertThat(dataService.generateAndPublishGpsTimeSeriesByFileGroup(5L, "_Summer__Trip_")
            .getIdentifier()).isEqualTo("summer_trip");
        assertThat(dataService.generateAndPublishGpsTimeSeriesByFileGroup(5L, "___")
            .getIdentifier()).isEqualTo("gps_timeseries");
        assertThat(dataService.generateAndPublishGpsTimeSeriesByFileGroup(5L, "x".repeat(70))
            .getIdentifier()).hasSize(60);
    }
}
