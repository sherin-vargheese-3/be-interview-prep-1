package com.example.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.interviewprep.model.ShortUrl;
import com.example.interviewprep.repository.ShortUrlRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class ShortUrlControllerTest {

	private static final String LONG_URL = "https://example.com/some/very/long/path?with=query";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ShortUrlRepository repository;

	@BeforeEach
	void clearShortUrls() {
		repository.deleteAll();
	}

	@Test
	void createReturnsCodeAndShortUrl() throws Exception {
		Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);

		ResultActions result = shorten(LONG_URL, expiresAt);

		result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value(matchesPattern("[A-Za-z0-9]{7}")))
				.andExpect(jsonPath("$.shortUrl").value(startsWith("http://localhost:8080/")));
		assertThat(repository.findAll()).singleElement()
				.satisfies(saved -> assertThat(saved.getExpiresAt()).isNotNull());
	}

	@ParameterizedTest
	@ValueSource(strings = {"ftp://example.com/file", "not a url", "example.com/no-scheme", "http://", "mailto:someone@example.com"})
	void invalidUrlIsRejectedWithFieldError(String url) throws Exception {
		ResultActions result = shorten(url, null);

		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors", hasSize(1)))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("url"));
	}

	@Test
	void blankUrlIsRejected() throws Exception {
		ResultActions result = shorten("", null);

		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("url"));
	}

	@Test
	void pastExpiryIsRejected() throws Exception {
		ResultActions result = shorten(LONG_URL, Instant.now().minus(1, ChronoUnit.HOURS));

		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("expiresAt"));
	}

	@Test
	void shorteningTheSameUrlTwiceGivesDifferentCodes() throws Exception {
		String first = createCode(LONG_URL);

		String second = createCode(LONG_URL);

		assertThat(first).isNotEqualTo(second);
		assertThat(repository.count()).isEqualTo(2);
	}

	@Test
	void shortCodeRedirectsToTheOriginalUrl() throws Exception {
		String code = createCode(LONG_URL);

		ResultActions result = mockMvc.perform(get("/{code}", code));

		result.andExpect(status().isFound())
				.andExpect(header().string("Location", LONG_URL));
		assertThat(visitCountOf(code)).isEqualTo(1);
	}

	@Test
	void unknownCodeReturnsNotFound() throws Exception {
		ResultActions result = mockMvc.perform(get("/{code}", "Zz9Zz9Z"));

		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.path").value("/Zz9Zz9Z"));
	}

	@Test
	void expiredCodeReturnsGoneWithoutCountingTheVisit() throws Exception {
		repository.save(new ShortUrl("Expired", LONG_URL, Instant.now().minus(1, ChronoUnit.MINUTES)));

		ResultActions result = mockMvc.perform(get("/{code}", "Expired"));

		result.andExpect(status().isGone())
				.andExpect(jsonPath("$.status").value(410))
				.andExpect(jsonPath("$.message").value("Short URL Expired has expired"));
		assertThat(visitCountOf("Expired")).isZero();
	}

	@Test
	void concurrentVisitsAreAllCounted() throws Exception {
		int visitors = 100;
		String code = createCode(LONG_URL);
		ExecutorService executor = Executors.newFixedThreadPool(visitors);
		CountDownLatch startGate = new CountDownLatch(1);
		List<Future<Integer>> statuses = new ArrayList<>();
		for (int i = 0; i < visitors; i++) {
			statuses.add(executor.submit(() -> {
				startGate.await();
				return mockMvc.perform(get("/{code}", code)).andReturn().getResponse().getStatus();
			}));
		}

		startGate.countDown();
		List<Integer> results = new ArrayList<>();
		for (Future<Integer> status : statuses) {
			results.add(status.get(30, TimeUnit.SECONDS));
		}
		executor.shutdown();

		assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		assertThat(results).hasSize(visitors).containsOnly(302);
		assertThat(visitCountOf(code)).isEqualTo(visitors);
	}

	private long visitCountOf(String code) {
		return repository.findByCode(code).map(ShortUrl::getVisitCount).orElseThrow();
	}

	private String createCode(String url) throws Exception {
		String response = shorten(url, null)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode created = objectMapper.readTree(response);
		return created.get("code").asText();
	}

	private ResultActions shorten(String url, Instant expiresAt) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("url", url);
		body.put("expiresAt", expiresAt);
		return mockMvc.perform(post("/api/urls")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}
}
