package com.example.interviewprep.exception;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.interviewprep.controller.TaskController;
import com.example.interviewprep.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
@WithMockUser
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TaskService taskService;

	@Test
	void unexpectedFailureReturnsGenericServerError() throws Exception {
		given(taskService.get(1L)).willThrow(new IllegalStateException("internal failure detail"));

		mockMvc.perform(get("/api/tasks/{id}", 1))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.error").value("Internal Server Error"))
				.andExpect(jsonPath("$.message").value("Unexpected error"))
				.andExpect(jsonPath("$.path").value("/api/tasks/1"))
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}
}
