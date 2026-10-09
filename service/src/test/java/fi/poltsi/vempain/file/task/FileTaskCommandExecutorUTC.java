package fi.poltsi.vempain.file.task;

import fi.poltsi.vempain.common.task.TaskProgress;
import fi.poltsi.vempain.common.task.entity.TaskCompensationEntity;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.request.PublishFileRequest;
import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.api.request.TagOperationRequest;
import fi.poltsi.vempain.file.service.DataService;
import fi.poltsi.vempain.file.service.FileScannerService;
import fi.poltsi.vempain.file.service.PublishService;
import fi.poltsi.vempain.file.service.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileTaskCommandExecutorUTC {

	@Mock
	private ApplicationContext applicationContext;
	@Mock
	private PublishService     publishService;
	@Mock
	private FileScannerService fileScannerService;
	@Mock
	private DataService        dataService;
	@Mock
	private TagService         tagService;

	private final ObjectMapper        mapper = new ObjectMapper();
	private FileTaskCommandExecutor executor;

	@BeforeEach
	void setUp() {
		executor = new FileTaskCommandExecutor(applicationContext, mapper);
	}

	@Test
	void executesPublishFileCommand() {
		var request = PublishFileRequest.builder()
										.fileId(55L)
										.build();
		var task = task("PUBLISH_FILE", request);
		when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

		assertThat(executor.execute(task)).isNull();

		verify(publishService).publishFileNow(request, task);
	}

	@Test
	void executesPublishFileGroupCommand() {
		var request = PublishFileGroupRequest.builder()
		                                     .fileGroupId(42L)
		                                     .galleryName("Gallery")
		                                     .build();
		var task    = task("PUBLISH_FILE_GROUP", request);
		when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);

		assertThat(executor.execute(task)).isNull();

		verify(publishService).publishFileGroupNow(request, task, true);
	}

	@Test
	void executesPublishAllAndScanCommands() {
		var publishAll  = task("PUBLISH_ALL_FILE_GROUPS", Map.of());
		var scanRequest = new ScanRequest("/original", "/export");
		var scan        = task("SCAN_DIRECTORIES", scanRequest);
		when(applicationContext.getBean(PublishService.class)).thenReturn(publishService);
		when(applicationContext.getBean(FileScannerService.class)).thenReturn(fileScannerService);
		when(fileScannerService.scanDirectories(scanRequest, scan)).thenReturn(null);

		assertThat(executor.execute(publishAll)).isNull();
		assertThat(executor.execute(scan)).isNull();

		verify(publishService).publishAllFileGroupsNow(publishAll);
		verify(fileScannerService).scanDirectories(scanRequest, scan);
	}

	@Test
	void executesMusicAndGpsCommands() {
		var music = task("PUBLISH_MUSIC_DATA", Map.of());
		var gps   = task("PUBLISH_GPS_TIME_SERIES", Map.of("file_group_id", 7L, "time_series_name", "Trip"));
		when(applicationContext.getBean(DataService.class)).thenReturn(dataService);
		when(dataService.publishMusicDatasetNow(music)).thenReturn(null);
		when(dataService.publishGpsTimeSeriesNow(7L, "Trip", gps)).thenReturn(null);

		assertThat(executor.execute(music)).isNull();
		assertThat(executor.execute(gps)).isNull();

		verify(dataService).publishMusicDatasetNow(music);
		verify(dataService).publishGpsTimeSeriesNow(7L, "Trip", gps);
	}

	@Test
	void executesTagCommandAndCompensation() {
		var request = new TagOperationRequest();
		request.setTagName("old");
		var payload = Map.of("request", request, "file_ids", List.of(1L, 2L), "operation", "REMOVE", "delete_unused_tag", true);
		var task    = task("TAG_REMOVE_FROM_ALL", payload);
		when(applicationContext.getBean(TagService.class)).thenReturn(tagService);
		var compensation = new TaskCompensationEntity();
		compensation.setCommandType("TAG_REVERT_MUTATION");
		compensation.setPayload(mapper.writeValueAsString(Map.of("file_id", 1L, "old_tag", "old", "new_tag", "", "operation", "REMOVE")));

		assertThat(executor.execute(task)).isNull();
		executor.compensate(compensation);

		verify(tagService).applyTagOperationForTask(request, List.of(1L, 2L), "REMOVE", true, task);
		verify(tagService).revertTagMutation(1L, "old", "", "REMOVE");
	}

	@Test
	void rejectsUnknownTaskAndCompensationCommands() {
		var unknownTask         = task("UNKNOWN", Map.of());
		var unknownCompensation = new TaskCompensationEntity();
		unknownCompensation.setCommandType("UNKNOWN");
		unknownCompensation.setPayload("{}");

		assertThrows(IllegalArgumentException.class, () -> executor.execute(unknownTask));
		assertThrows(IllegalArgumentException.class, () -> executor.compensate(unknownCompensation));
	}

	private TaskProgress task(String type, Object payload) {
		return TaskProgress.unmanaged("task-" + type, type, "title", 1L, 1, mapper.writeValueAsString(payload));
	}
}
