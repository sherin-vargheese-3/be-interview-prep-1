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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
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

	@Test
	void accessDeniedReachingMvcReturnsForbidden() throws Exception {
		given(taskService.get(2L)).willThrow(new AccessDeniedException("internal reason"));

		mockMvc.perform(get("/api/tasks/{id}", 2))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.message").value("Access denied"));
	}

	@Test
	void authenticationFailureReachingMvcReturnsUnauthorized() throws Exception {
		given(taskService.get(3L)).willThrow(new BadCredentialsException("internal reason"));

		mockMvc.perform(get("/api/tasks/{id}", 3))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").value("Authentication required"));
	}
}
