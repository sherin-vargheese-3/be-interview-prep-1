package com.example.interviewprep.service;

import com.example.interviewprep.dto.ShortUrlRequest;
import com.example.interviewprep.dto.ShortUrlResponse;
import com.example.interviewprep.exception.ShortCodeGenerationException;
import com.example.interviewprep.model.ShortUrl;
import com.example.interviewprep.repository.ShortUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ShortUrlService {

	private static final Logger log = LoggerFactory.getLogger(ShortUrlService.class);

	private static final int MAX_CODE_ATTEMPTS = 5;

	private final ShortUrlRepository repository;

	private final ShortCodeGenerator codeGenerator;

	private final String baseUrl;

	public ShortUrlService(
			ShortUrlRepository repository,
			ShortCodeGenerator codeGenerator,
			@Value("${app.base-url}") String baseUrl) {
		this.repository = repository;
		this.codeGenerator = codeGenerator;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	public ShortUrlResponse create(ShortUrlRequest request) {
		for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
			String code = codeGenerator.generate();
			if (repository.existsByCode(code)) {
				log.warn("Short code collision on attempt {}", attempt);
				continue;
			}
			try {
				ShortUrl saved = repository.saveAndFlush(new ShortUrl(code, request.url(), request.expiresAt()));
				return toResponse(saved);
			} catch (DataIntegrityViolationException ex) {
				log.warn("Short code taken concurrently on attempt {}", attempt);
			}
		}
		throw new ShortCodeGenerationException("Could not allocate a unique short code, please retry");
	}

	private ShortUrlResponse toResponse(ShortUrl shortUrl) {
		return new ShortUrlResponse(shortUrl.getCode(), baseUrl + "/" + shortUrl.getCode());
	}
}
