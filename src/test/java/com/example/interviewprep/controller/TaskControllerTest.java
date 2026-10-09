package com.example.interviewprep.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.interviewprep.dto.TaskRequest;
import com.example.interviewprep.enums.TaskStatus;
import com.example.interviewprep.repository.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class TaskControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private TaskRepository repository;

	@BeforeEach
	void clearTasks() {
		repository.deleteAll();
	}

	@Test
	void createThenGetReturnsTheTask() throws Exception {
		TaskRequest request = new TaskRequest("Write tests", "for Q1", null, LocalDate.now().plusDays(1));

		long id = createTask(request);

		mockMvc.perform(get("/api/tasks/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Write tests"))
				.andExpect(jsonPath("$.status").value("TODO"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	void invalidInputReturnsFieldErrors() throws Exception {
		String body = """
				{"title": "", "dueDate": "2000-01-01"}
				""";

		mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors", hasSize(2)))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'dueDate')]").exists());
	}

	@Test
	void titleLongerThan100CharactersIsRejected() throws Exception {
		TaskRequest request = new TaskRequest("x".repeat(101), null, null, null);

		mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(json(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
	}

	@Test
	void malformedBodyReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"NOPE\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed request body"));
	}

	@Test
	void unknownTaskReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/tasks/{id}", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.path").value("/api/tasks/999999"));
	}

	@Test
	void listFiltersByStatus() throws Exception {
		createTask(new TaskRequest("todo", null, TaskStatus.TODO, null));
		createTask(new TaskRequest("done", null, TaskStatus.DONE, null));

		mockMvc.perform(get("/api/tasks").param("status", "DONE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].title").value("done"));
		mockMvc.perform(get("/api/tasks"))
				.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void invalidStatusFilterReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/tasks").param("status", "LATER"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
	}

	@Test
	void updateReplacesTheTask() throws Exception {
		long id = createTask(new TaskRequest("old", null, null, null));
		TaskRequest update = new TaskRequest("new", "changed", TaskStatus.IN_PROGRESS, LocalDate.now());

		mockMvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("new"))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}

	@Test
	void updateWithPastDueDateIsRejected() throws Exception {
		long id = createTask(new TaskRequest("task", null, null, null));
		TaskRequest update = new TaskRequest("task", null, null, LocalDate.now().minusDays(1));

		mockMvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json(update)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("dueDate"));
	}

	@Test
	void deleteRemovesTheTask() throws Exception {
		long id = createTask(new TaskRequest("temp", null, null, null));

		mockMvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isNotFound());
	}

	@Test
	void unsupportedMethodReturnsJsonError() throws Exception {
		mockMvc.perform(post("/api/tasks/{id}", 1))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.status").value(405));
	}

	private long createTask(TaskRequest request) throws Exception {
		String response = mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(json(request)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode created = objectMapper.readTree(response);
		return created.get("id").asLong();
	}

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}
}
