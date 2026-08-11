package fi.poltsi.vempain.file.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class FileStatisticsResponse {
	private long                            totalFiles;
	private Map<String, Long>               filesByType;
	private Map<String, Map<Integer, Long>> filesByTypeAndYear;
	private long                            totalTags;
	private long                            totalGpsLocations;
	private long                            filesWithGpsLocations;
	private long                            filesWithTags;
	private long                            largestFileGroupSize;
	private long                            smallestFileGroupSize;
	private BigDecimal                      averageFileGroupSize;
	private long                            totalFileSize;
	private long                            largestFileSize;
	private BigDecimal                      averageFileSize;
	private Map<String, Long>               largestFileSizeByType;
	private Map<String, BigDecimal>         averageFileSizeByType;
}
