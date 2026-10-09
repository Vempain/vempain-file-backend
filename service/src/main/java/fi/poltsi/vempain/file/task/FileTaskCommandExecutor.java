package fi.poltsi.vempain.file.task;

import fi.poltsi.vempain.common.task.TaskCommandExecutor;
import fi.poltsi.vempain.common.task.TaskProgress;
import fi.poltsi.vempain.common.task.entity.TaskCompensationEntity;
import fi.poltsi.vempain.file.api.TaskTypeEnum;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.request.PublishFileRequest;
import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.api.request.TagOperationRequest;
import fi.poltsi.vempain.file.service.DataService;
import fi.poltsi.vempain.file.service.FileScannerService;
import fi.poltsi.vempain.file.service.PublishService;
import fi.poltsi.vempain.file.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * The file backend's {@link TaskCommandExecutor}: converts durable task commands into service calls. It deliberately resolves
 * services through the Spring proxy so transactional methods are invoked correctly on worker threads.
 */
@Service
@RequiredArgsConstructor
public class FileTaskCommandExecutor implements TaskCommandExecutor {
	private final ApplicationContext applicationContext;
	private final ObjectMapper       objectMapper;

	@Override
	public Object execute(TaskProgress progress) {
		var      type    = TaskTypeEnum.valueOf(progress.getType());
		JsonNode payload = objectMapper.readTree(progress.getPayload() == null ? "{}" : progress.getPayload());
		return switch (type) {
			case PUBLISH_FILE -> {
				applicationContext.getBean(PublishService.class)
								  .publishFileNow(objectMapper.treeToValue(payload, PublishFileRequest.class), progress);
				yield null;
			}
			case PUBLISH_FILE_GROUP -> {
				var service = applicationContext.getBean(PublishService.class);
				service.publishFileGroupNow(objectMapper.treeToValue(payload, PublishFileGroupRequest.class), progress, true);
				yield null;
			}
			case PUBLISH_ALL_FILE_GROUPS -> {
				applicationContext.getBean(PublishService.class)
				                  .publishAllFileGroupsNow(progress);
				yield null;
			}
			case SCAN_DIRECTORIES -> applicationContext.getBean(FileScannerService.class)
													   .scanDirectories(objectMapper.treeToValue(payload, ScanRequest.class), progress);
			case PUBLISH_MUSIC_DATA -> applicationContext.getBean(DataService.class)
			                                             .publishMusicDatasetNow(progress);
			case PUBLISH_GPS_TIME_SERIES -> {
				var fileGroupId = payload.path("file_group_id")
				                         .asLong();
				var name        = payload.path("time_series_name")
				                         .asText();
				yield applicationContext.getBean(DataService.class)
				                        .publishGpsTimeSeriesNow(fileGroupId, name, progress);
			}
			case TAG_REMOVE_FROM_ALL, TAG_REPLACE_ACROSS_ALL, TAG_RENAME_ACROSS_ALL -> {
				var request      = objectMapper.treeToValue(payload.path("request"), TagOperationRequest.class);
				var fileIds      = objectMapper.convertValue(payload.path("file_ids"), List.class);
				var ids          = fileIds.stream()
				                          .map(value -> ((Number) value).longValue())
				                          .toList();
				var operation    = payload.path("operation")
				                          .asText();
				var deleteUnused = payload.path("delete_unused_tag")
				                          .asBoolean();
				applicationContext.getBean(TagService.class)
				                  .applyTagOperationForTask(request, ids, operation, deleteUnused, progress);
				yield null;
			}
		};
	}

	@Override
	public void compensate(TaskCompensationEntity compensation) {
		var payload = objectMapper.readTree(compensation.getPayload());
		switch (compensation.getCommandType()) {
			case "TAG_REVERT_MUTATION" -> applicationContext.getBean(TagService.class)
			                                                .revertTagMutation(
																	payload.path("file_id")
					                                                       .asLong(), payload.path("old_tag")
					                                                                         .asText(null),
																	payload.path("new_tag")
					                                                       .asText(null), payload.path("operation")
					                                                                             .asText());
			default -> throw new IllegalArgumentException("Unknown task compensation command: " + compensation.getCommandType());
		}
	}
}
