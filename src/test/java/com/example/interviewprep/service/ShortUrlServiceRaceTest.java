package com.example.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.interviewprep.dto.ShortUrlRequest;
import com.example.interviewprep.dto.ShortUrlResponse;
import com.example.interviewprep.exception.ShortCodeGenerationException;
import com.example.interviewprep.model.ShortUrl;
import com.example.interviewprep.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceRaceTest {

	private static final String URL = "https://example.com";

	@Mock
	private ShortUrlRepository repository;

	@Mock
	private ShortCodeGenerator codeGenerator;

	private ShortUrlService service;

	@BeforeEach
	void createService() {
		service = new ShortUrlService(repository, codeGenerator, "http://short.test/");
	}

	@Test
	void codeTakenByAConcurrentInsertIsRetriedWithAFreshOne() {
		when(codeGenerator.generate()).thenReturn("RACED01", "FRESH01");
		when(repository.existsByCode(anyString())).thenReturn(false);
		when(repository.saveAndFlush(any(ShortUrl.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate code"))
				.thenAnswer(invocation -> invocation.getArgument(0));

		ShortUrlResponse response = service.create(new ShortUrlRequest(URL, null));

		assertThat(response.code()).isEqualTo("FRESH01");
		assertThat(response.shortUrl()).isEqualTo("http://short.test/FRESH01");
		verify(repository, times(2)).saveAndFlush(any(ShortUrl.class));
	}

	@Test
	void concurrentInsertsOnEveryAttemptFailWithServiceUnavailable() {
		when(codeGenerator.generate()).thenReturn("RACED01");
		when(repository.existsByCode(anyString())).thenReturn(false);
		when(repository.saveAndFlush(any(ShortUrl.class))).thenThrow(new DataIntegrityViolationException("duplicate code"));

		ShortCodeGenerationException ex = assertThrows(ShortCodeGenerationException.class,
				() -> service.create(new ShortUrlRequest(URL, null)));

		assertThat(ex.getStatus().value()).isEqualTo(503);
		verify(repository, times(5)).saveAndFlush(any(ShortUrl.class));
	}
}
