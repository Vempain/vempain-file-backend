package fi.poltsi.vempain.file.repository.files;

import java.math.BigDecimal;

public interface FileTypeSizeProjection {
	String getFileType();

	Long getLargestFileSize();

	BigDecimal getAverageFileSize();
}
