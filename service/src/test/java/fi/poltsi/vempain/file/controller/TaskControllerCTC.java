package fi.poltsi.vempain.file.controller;

import fi.poltsi.vempain.file.task.TaskProgress;
import fi.poltsi.vempain.file.task.TaskProgressStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller Test Class (CTC) for the task progress API ({@code TaskAPI}): listing, polling and dismissing background tasks,
 * which are private to the user who started them.
 */
class TaskControllerCTC extends AbstractControllerCTC {

	@Autowired
	private TaskProgressStore store;

	@Test
	void listsOnlyTheCallersTasksAndPollsThem() throws Exception {
		var own   = store.create("SCAN_DIRECTORIES", "Scan /photos", 1L, 4);
		var other = store.create("SCAN_DIRECTORIES", "Not mine", 2L, 1);
		own.advance("Scanned /photos/2024");
		own.advance("Scanned /photos/2025");

		try {
			doGet("/tasks")
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[?(@.task_id == '" + own.getId() + "')]", hasSize(1)))
					.andExpect(jsonPath("$[?(@.task_id == '" + other.getId() + "')]", hasSize(0)));

			doGet("/tasks/" + own.getId())
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.task_id").value(own.getId()))
					.andExpect(jsonPath("$.type").value("SCAN_DIRECTORIES"))
					.andExpect(jsonPath("$.title").value("Scan /photos"))
					.andExpect(jsonPath("$.status").value("QUEUED"))
					.andExpect(jsonPath("$.total_steps").value(4))
					.andExpect(jsonPath("$.completed_steps").value(2))
					.andExpect(jsonPath("$.failed_steps").value(0))
					.andExpect(jsonPath("$.percent").value(50))
					.andExpect(jsonPath("$.message").value("Scanned /photos/2025"))
					.andExpect(jsonPath("$.result").doesNotExist());

			doGet("/tasks/" + other.getId())
					.andExpect(status().isNotFound());
			doGet("/tasks/does-not-exist")
					.andExpect(status().isNotFound());
		} finally {
			store.remove(own.getId());
			store.remove(other.getId());
		}
	}

	@Test
	void dismissingIsOnlyAllowedForFinishedOwnTasks() throws Exception {
		var running  = store.create("PUBLISH_FILE_GROUP", "Publish", 1L, 0);
		var finished = store.create("PUBLISH_FILE_GROUP", "Publish", 1L, 0);
		var other    = store.create("PUBLISH_FILE_GROUP", "Publish", 2L, 0);
		ReflectionTestUtils.invokeMethod(finished, "complete", (Object) null);
		ReflectionTestUtils.invokeMethod(other, "complete", (Object) null);

		try {
			doDelete("/tasks/" + running.getId())
					.andExpect(status().isConflict());
			doDelete("/tasks/" + other.getId())
					.andExpect(status().isNotFound());
			doDelete("/tasks/" + finished.getId())
					.andExpect(status().isNoContent());
			doGet("/tasks/" + finished.getId())
					.andExpect(status().isNotFound());
		} finally {
			store.remove(running.getId());
			store.remove(other.getId());
		}
	}

	@Test
	void cancelMarksOwnActiveTasksAndRejectsOthers() throws Exception {
		var queued   = store.create("SCAN_DIRECTORIES", "Scan", 1L, 3);
		var finished = store.create("SCAN_DIRECTORIES", "Scan", 1L, 0);
		var other    = store.create("SCAN_DIRECTORIES", "Scan", 2L, 0);
		ReflectionTestUtils.invokeMethod(finished, "complete", (Object) null);

		try {
			mockMvc.perform(post("/tasks/" + queued.getId() + "/cancel").with(user(CTC_PRINCIPAL))
																		.with(csrf()))
				   .andExpect(status().isAccepted())
				   .andExpect(jsonPath("$.task_id").value(queued.getId()))
				   .andExpect(jsonPath("$.cancel_requested").value(true))
				   .andExpect(jsonPath("$.reverted_steps").value(0));
			assertThat(queued.isCancelRequested()).isTrue();

			mockMvc.perform(post("/tasks/" + finished.getId() + "/cancel").with(user(CTC_PRINCIPAL))
																		  .with(csrf()))
				   .andExpect(status().isConflict());
			mockMvc.perform(post("/tasks/" + other.getId() + "/cancel").with(user(CTC_PRINCIPAL))
																	   .with(csrf()))
				   .andExpect(status().isNotFound());
		} finally {
			store.remove(queued.getId());
			store.remove(finished.getId());
			store.remove(other.getId());
		}
	}

	@Test
	void finishedTaskExposesItsResultPayload() throws Exception {
		TaskProgress task = store.create("PUBLISH_MUSIC_DATA", "Publish music data set", 1L, 1);
		ReflectionTestUtils.invokeMethod(task, "start");
		task.advance("Published");
		ReflectionTestUtils.invokeMethod(task, "complete", java.util.Map.of("identifier", "music_library"));

		try {
			doGet("/tasks/" + task.getId())
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("COMPLETED"))
					.andExpect(jsonPath("$.percent").value(100))
					.andExpect(jsonPath("$.result.identifier").value("music_library"))
					.andExpect(jsonPath("$.started_at").exists())
					.andExpect(jsonPath("$.finished_at").exists());
		} finally {
			store.remove(task.getId());
		}
	}
}
