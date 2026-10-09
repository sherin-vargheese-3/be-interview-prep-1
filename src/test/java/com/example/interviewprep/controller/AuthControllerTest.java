package com.example.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.interviewprep.config.JwtProperties;
import com.example.interviewprep.dto.LoginRequest;
import com.example.interviewprep.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

	private static final String PASSWORD = "dummy-pass-123";

	private static final String INVALID_CREDENTIALS = "Invalid email or password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Test
	void registerCreatesAUserWithoutExposingThePassword() throws Exception {
		String email = uniqueEmail();

		ResultActions result = register(email, PASSWORD);

		result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void registerNormalizesTheEmail() throws Exception {
		String email = "Mixed." + UUID.randomUUID() + "@Example.COM";

		ResultActions result = register(email, PASSWORD);

		result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email.toLowerCase()));
	}

	@Test
	void registerIgnoresARequestedRole() throws Exception {
		Map<String, String> body = Map.of("email", uniqueEmail(), "password", PASSWORD, "role", "ADMIN");

		ResultActions result = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));

		result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void duplicateEmailReturnsConflict() throws Exception {
		String email = uniqueEmail();
		register(email, PASSWORD).andExpect(status().isCreated());

		ResultActions result = register(email.toUpperCase(), PASSWORD);

		result.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value("Email is already registered"));
	}

	@Test
	void invalidRegistrationReturnsFieldErrors() throws Exception {
		RegisterRequest request = new RegisterRequest("not-an-email", "short");

		ResultActions result = mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));

		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors", hasSize(2)))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
				.andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists());
	}

	@Test
	void passwordLongerThan72CharactersIsRejected() throws Exception {
		ResultActions result = register(uniqueEmail(), "x".repeat(73));

		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
	}

	@Test
	void loginReturnsABearerTokenForFifteenMinutes() throws Exception {
		String email = uniqueEmail();
		register(email, PASSWORD).andExpect(status().isCreated());

		ResultActions result = login(email, PASSWORD);

		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(900));
	}

	@Test
	void issuedTokenCarriesTheUserIdentityAndRole() throws Exception {
		String email = uniqueEmail();
		String userId = objectMapper.readTree(register(email, PASSWORD).andReturn().getResponse().getContentAsString())
				.get("id").asText();

		String token = objectMapper.readTree(login(email, PASSWORD).andReturn().getResponse().getContentAsString())
				.get("accessToken").asText();
		Jwt jwt = jwtDecoder.decode(token);

		assertThat(jwt.getSubject()).isEqualTo(userId);
		assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtProperties.ISSUER);
		assertThat(jwt.getClaimAsString("email")).isEqualTo(email);
		assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("USER"));
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
		assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
	}

	@Test
	void wrongPasswordReturnsGenericUnauthorized() throws Exception {
		String email = uniqueEmail();
		register(email, PASSWORD).andExpect(status().isCreated());

		ResultActions result = login(email, "dummy-wrong-pass");

		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
	}

	@Test
	void unknownEmailReturnsTheSameUnauthorizedMessage() throws Exception {
		ResultActions result = login(uniqueEmail(), PASSWORD);

		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
	}

	@Test
	void blankLoginReturnsFieldErrors() throws Exception {
		LoginRequest request = new LoginRequest("", "");

		ResultActions result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));

		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors", hasSize(2)));
	}

	private ResultActions register(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new RegisterRequest(email, password))));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}
}
