package fi.poltsi.vempain.file.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class DirectoryProcessorServiceUTC {

	@InjectMocks
	private DirectoryProcessorService directoryProcessorService;

	@ParameterizedTest
	@CsvSource({
			"2006:11:19 00:04:34+02:00",
			"2008:03:20 21:14:46.00+02:00",
			"2014:06:24 09:34:01.761+03:00",
	})
	void dateTimeParser(String dateTimeString) {
		var dateTime = directoryProcessorService.dateTimeParser(dateTimeString);
		assertNotNull(dateTime);
	}
}
