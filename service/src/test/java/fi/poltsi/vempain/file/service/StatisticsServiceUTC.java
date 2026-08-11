package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.repository.GpsLocationRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.repository.files.FileTypeCountProjection;
import fi.poltsi.vempain.file.repository.files.FileTypeSizeProjection;
import fi.poltsi.vempain.file.repository.files.FileTypeYearCountProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceUTC {
	@Mock
	private FileRepository        fileRepository;
	@Mock
	private TagRepository         tagRepository;
	@Mock
	private GpsLocationRepository gpsLocationRepository;
	@InjectMocks
	private StatisticsService     statisticsService;

	@Test
	void getStatistics_assemblesAllMetrics() {
		var type = mock(FileTypeCountProjection.class);
		when(type.getFileType()).thenReturn("IMAGE");
		when(type.getFileCount()).thenReturn(3L);
		var year = mock(FileTypeYearCountProjection.class);
		when(year.getFileType()).thenReturn("IMAGE");
		when(year.getCreationYear()).thenReturn(2024);
		when(year.getFileCount()).thenReturn(2L);
		var size = mock(FileTypeSizeProjection.class);
		when(size.getFileType()).thenReturn("IMAGE");
		when(size.getLargestFileSize()).thenReturn(900L);
		when(size.getAverageFileSize()).thenReturn(new BigDecimal("450.125"));
		when(fileRepository.countFilesByType()).thenReturn(List.of(type));
		when(fileRepository.countFilesByTypeAndCreationYear()).thenReturn(List.of(year));
		when(fileRepository.summarizeFileSizesByType()).thenReturn(List.of(size));
		when(fileRepository.findFileGroupSizes()).thenReturn(List.of(2L, 4L));
		when(fileRepository.countTotalFiles()).thenReturn(3L);
		when(fileRepository.countFilesWithGpsLocations()).thenReturn(1L);
		when(fileRepository.countFilesWithTags()).thenReturn(2L);
		when(fileRepository.sumFileSizes()).thenReturn(1350L);
		when(fileRepository.maxFileSize()).thenReturn(900L);
		when(fileRepository.averageFileSize()).thenReturn(new BigDecimal("450.125"));
		when(tagRepository.count()).thenReturn(5L);
		when(gpsLocationRepository.count()).thenReturn(2L);

		var response = statisticsService.getStatistics();

		assertThat(response.getTotalFiles()).isEqualTo(3);
		assertThat(response.getFilesByType()).containsEntry("image", 3L);
		assertThat(response.getFilesByTypeAndYear()).containsEntry("image", java.util.Map.of(2024, 2L));
		assertThat(response.getTotalTags()).isEqualTo(5);
		assertThat(response.getTotalGpsLocations()).isEqualTo(2);
		assertThat(response.getFilesWithGpsLocations()).isEqualTo(1);
		assertThat(response.getFilesWithTags()).isEqualTo(2);
		assertThat(response.getLargestFileGroupSize()).isEqualTo(4);
		assertThat(response.getSmallestFileGroupSize()).isEqualTo(2);
		assertThat(response.getAverageFileGroupSize()).isEqualByComparingTo("3");
		assertThat(response.getAverageFileSize()).isEqualByComparingTo("450.13");
		assertThat(response.getAverageFileSizeByType()).containsEntry("image", new BigDecimal("450.13"));
	}

	@Test
	void getStatistics_emptyDatabaseReturnsZeroesAndEmptyMaps() {
		when(fileRepository.countFilesByType()).thenReturn(List.of());
		when(fileRepository.countFilesByTypeAndCreationYear()).thenReturn(List.of());
		when(fileRepository.summarizeFileSizesByType()).thenReturn(List.of());
		when(fileRepository.findFileGroupSizes()).thenReturn(List.of());
		when(fileRepository.averageFileSize()).thenReturn(BigDecimal.ZERO);

		var response = statisticsService.getStatistics();

		assertThat(response.getTotalFiles()).isZero();
		assertThat(response.getLargestFileGroupSize()).isZero();
		assertThat(response.getSmallestFileGroupSize()).isZero();
		assertThat(response.getAverageFileGroupSize()).isZero();
		assertThat(response.getFilesByType()).isEmpty();
		assertThat(response.getAverageFileSize()).isZero();
	}
}
