package fi.poltsi.vempain.file.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import fi.poltsi.vempain.file.api.response.FileStatisticsResponse;
import fi.poltsi.vempain.file.rest.StatisticsAPI;
import fi.poltsi.vempain.file.service.StatisticsService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class StatisticsController implements StatisticsAPI {

    private final StatisticsService statisticsService;

    @Override
    public ResponseEntity<FileStatisticsResponse> getStatistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }
}
