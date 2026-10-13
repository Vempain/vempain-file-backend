package fi.poltsi.vempain.file.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for {@link StatisticsController}: {@code GET /api/statistics} answers the system wide file counts.
 */
class StatisticsControllerCTC extends AbstractControllerCTC {

	@Test
	void getStatistics_answersTheCountsAsJson() throws Exception {
		doGet("/statistics")
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$").isMap());
	}
}
