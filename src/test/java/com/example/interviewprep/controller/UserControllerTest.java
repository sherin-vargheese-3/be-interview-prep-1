package com.example.interviewprep.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.interviewprep.config.AdminProperties;
import com.example.interviewprep.config.JwtProperties;
import com.example.interviewprep.dto.LoginRequest;
import com.example.interviewprep.dto.RegisterRequest;
import com.example.interviewprep.model.ShortUrl;
import com.example.interviewprep.repository.ShortUrlRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

	private static final String PASSWORD = "dummy-pass-123";

	private static final String OTHER_SIGNING_KEY = "fake-other-signing-key-for-tests-only-0123456789";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private AdminProperties adminProperties;

	@Autowired
	private ShortUrlRepository shortUrlRepository;

	@Test
	void meReturnsTheCallersOwnProfile() throws Exception {
		String email = registerUser();
		String token = loginAs(email, PASSWORD);

		ResultActions result = mockMvc.perform(withBearer(get("/api/users/me"), token));

		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void meWithoutTokenReturnsJsonUnauthorized() throws Exception {
		ResultActions result = mockMvc.perform(get("/api/users/me"));

		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Authentication required"))
				.andExpect(jsonPath("$.path").value("/api/users/me"));
	}

	@Test
	void userTokenOnAdminEndpointReturnsJsonForbidden() throws Exception {
		String token = loginAs(registerUser(), PASSWORD);

		ResultActions result = mockMvc.perform(withBearer(get("/api/admin/users"), token));

		result.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.error").value("Forbidden"))
				.andExpect(jsonPath("$.message").value("Access denied"))
				.andExpect(jsonPath("$.path").value("/api/admin/users"));
	}

	@Test
	void seededAdminCanListAllUsers() throws Exception {
		String userEmail = registerUser();
		String token = loginAs(adminProperties.email(), adminProperties.password());

		ResultActions result = mockMvc.perform(withBearer(get("/api/admin/users"), token));

		result.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.email == '" + adminProperties.email() + "' && @.role == 'ADMIN')]").exists())
				.andExpect(jsonPath("$[?(@.email == '" + userEmail + "' && @.role == 'USER')]").exists())
				.andExpect(jsonPath("$[*].password").doesNotExist());
	}

	@Test
	void expiredTokenReturnsJsonUnauthorized() throws Exception {
		Instant now = Instant.now();
		String token = mint(jwtEncoder, now.minus(20, ChronoUnit.MINUTES), now.minus(5, ChronoUnit.MINUTES), "USER");

		ResultActions result = mockMvc.perform(withBearer(get("/api/users/me"), token));

		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").value("Authentication required"));
	}

	@Test
	void tokenSignedWithAnotherKeyReturnsUnauthorized() throws Exception {
		JwtEncoder foreignEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
				new SecretKeySpec(OTHER_SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		Instant now = Instant.now();
		String token = mint(foreignEncoder, now, now.plus(5, ChronoUnit.MINUTES), "ADMIN");

		ResultActions result = mockMvc.perform(withBearer(get("/api/admin/users"), token));

		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void tamperedRoleClaimReturnsUnauthorized() throws Exception {
		String token = loginAs(registerUser(), PASSWORD);
		String[] parts = token.split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
		String escalatedPayload = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payload.replace("\"USER\"", "\"ADMIN\"").getBytes(StandardCharsets.UTF_8));
		String tampered = parts[0] + "." + escalatedPayload + "." + parts[2];

		ResultActions result = mockMvc.perform(withBearer(get("/api/admin/users"), tampered));

		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void malformedTokenReturnsUnauthorized() throws Exception {
		ResultActions result = mockMvc.perform(withBearer(get("/api/users/me"), "not-a-jwt"));

		result.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.message").value("Authentication required"));
	}

	@Test
	void taskApiWithoutTokenReturnsUnauthorized() throws Exception {
		ResultActions result = mockMvc.perform(get("/api/tasks"));

		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void taskApiWithTokenIsAllowed() throws Exception {
		String token = loginAs(registerUser(), PASSWORD);

		ResultActions result = mockMvc.perform(withBearer(get("/api/tasks"), token));

		result.andExpect(status().isOk());
	}

	@Test
	void shortLinkRedirectStaysPublic() throws Exception {
		String code = "Pub" + UUID.randomUUID().toString().replace("-", "").substring(0, 4);
		shortUrlRepository.save(new ShortUrl(code, "https://example.com/public", null));

		ResultActions result = mockMvc.perform(get("/{code}", code));

		result.andExpect(status().isFound())
				.andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/public"));
	}

	@Test
	void userTokenCanReadProducts() throws Exception {
		String token = loginAs(registerUser(), PASSWORD);

		ResultActions result = mockMvc.perform(withBearer(get("/api/products"), token));

		result.andExpect(status().isOk());
	}

	@Test
	void userTokenCannotUpdateProducts() throws Exception {
		String token = loginAs(registerUser(), PASSWORD);
		String body = """
				{"name": "Hijacked", "category": "Home", "price": 1.00, "stock": 1, "rating": 1.0}
				""";

		ResultActions result = mockMvc.perform(withBearer(put("/api/products/{id}", 1), token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));

		result.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void healthIsPublicButOtherActuatorEndpointsNeedAdmin() throws Exception {
		String token = loginAs(registerUser(), PASSWORD);

		ResultActions health = mockMvc.perform(get("/actuator/health"));
		ResultActions metrics = mockMvc.perform(withBearer(get("/actuator/metrics"), token));

		health.andExpect(status().isOk());
		metrics.andExpect(status().isForbidden());
	}

	private String registerUser() throws Exception {
		String email = "user-" + UUID.randomUUID() + "@example.com";
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD))))
				.andExpect(status().isCreated());
		return email;
	}

	private String loginAs(String email, String password) throws Exception {
		String response = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response).get("accessToken").asText();
	}

	private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private static String mint(JwtEncoder encoder, Instant issuedAt, Instant expiresAt, String role) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(JwtProperties.ISSUER)
				.subject("1")
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim("roles", List.of(role))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
