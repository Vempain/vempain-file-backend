package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.api.response.FileStatisticsResponse;
import fi.poltsi.vempain.file.repository.GpsLocationRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {
	private final FileRepository        fileRepository;
	private final TagRepository         tagRepository;
	private final GpsLocationRepository gpsLocationRepository;

	public FileStatisticsResponse getStatistics() {
		final List<Long> groupSizes = fileRepository.findFileGroupSizes();
		final Map<String, Long> filesByType = fileRepository.countFilesByType()
		                                                    .stream()
															.collect(Collectors.toMap(
																	projection -> fileTypeName(projection.getFileType()),
																	projection -> projection.getFileCount(),
																	Long::sum,
																	LinkedHashMap::new));
		final Map<String, Map<Integer, Long>> filesByTypeAndYear = fileRepository.countFilesByTypeAndCreationYear()
		                                                                         .stream()
																				 .collect(Collectors.groupingBy(
																						 projection -> fileTypeName(projection.getFileType()),
																						 LinkedHashMap::new,
																						 Collectors.toMap(
																								 projection -> projection.getCreationYear(),
																								 projection -> projection.getFileCount(),
																								 Long::sum,
																								 LinkedHashMap::new)));
		final Map<String, Long>       largestByType = new LinkedHashMap<>();
		final Map<String, BigDecimal> averageByType = new LinkedHashMap<>();
		fileRepository.summarizeFileSizesByType()
		              .forEach(projection -> {
						  final String type = fileTypeName(projection.getFileType());
						  largestByType.put(type, projection.getLargestFileSize());
						  averageByType.put(type, scale(projection.getAverageFileSize()));
					  });

		return FileStatisticsResponse.builder()
									 .totalFiles(fileRepository.countTotalFiles())
									 .filesByType(filesByType)
									 .filesByTypeAndYear(filesByTypeAndYear)
									 .totalTags(tagRepository.count())
									 .totalGpsLocations(gpsLocationRepository.count())
									 .filesWithGpsLocations(fileRepository.countFilesWithGpsLocations())
									 .filesWithTags(fileRepository.countFilesWithTags())
									 .largestFileGroupSize(groupSizes.stream()
		                                                             .max(Comparator.naturalOrder())
		                                                             .orElse(0L))
									 .smallestFileGroupSize(groupSizes.stream()
		                                                              .min(Comparator.naturalOrder())
		                                                              .orElse(0L))
									 .averageFileGroupSize(average(groupSizes))
									 .totalFileSize(fileRepository.sumFileSizes())
									 .largestFileSize(fileRepository.maxFileSize())
									 .averageFileSize(scale(fileRepository.averageFileSize()))
									 .largestFileSizeByType(largestByType)
									 .averageFileSizeByType(averageByType)
									 .build();
	}

	private String fileTypeName(String fileType) {
		return FileTypeEnum.valueOf(fileType).shortName;
	}

	private BigDecimal average(List<Long> values) {
		return values.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(values.stream()
		                                                                     .mapToLong(Long::longValue)
		                                                                     .average()
		                                                                     .orElse(0));
	}

	private BigDecimal scale(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
	}
}
