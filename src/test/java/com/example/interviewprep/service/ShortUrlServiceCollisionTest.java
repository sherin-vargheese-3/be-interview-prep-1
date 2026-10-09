package com.example.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.example.interviewprep.dto.ShortUrlRequest;
import com.example.interviewprep.dto.ShortUrlResponse;
import com.example.interviewprep.exception.ShortCodeGenerationException;
import com.example.interviewprep.model.ShortUrl;
import com.example.interviewprep.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ShortUrlServiceCollisionTest {

	private static final String URL = "https://example.com";

	@Autowired
	private ShortUrlService service;

	@Autowired
	private ShortUrlRepository repository;

	@MockitoBean
	private ShortCodeGenerator codeGenerator;

	@BeforeEach
	void seedExistingCode() {
		repository.deleteAll();
		repository.save(new ShortUrl("TAKEN01", URL, null));
	}

	@Test
	void collidingCodeIsRetriedWithAFreshOne() {
		when(codeGenerator.generate()).thenReturn("TAKEN01", "FRESH01");

		ShortUrlResponse response = service.create(new ShortUrlRequest(URL, null));

		assertThat(response.code()).isEqualTo("FRESH01");
		assertThat(repository.count()).isEqualTo(2);
	}

	@Test
	void persistentCollisionsFailAfterMaxAttempts() {
		when(codeGenerator.generate()).thenReturn("TAKEN01");

		ShortCodeGenerationException ex = assertThrows(ShortCodeGenerationException.class,
				() -> service.create(new ShortUrlRequest(URL, null)));

		assertThat(ex.getStatus().value()).isEqualTo(503);
		assertThat(repository.count()).isEqualTo(1);
	}
}
