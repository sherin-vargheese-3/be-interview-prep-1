package com.example.interviewprep.dto;

import com.example.interviewprep.model.ShortUrl;
import java.time.Instant;

public record ShortUrlStatsResponse(
		String code,
		String originalUrl,
		long visitCount,
		Instant createdAt,
		Instant expiresAt) {

	public static ShortUrlStatsResponse from(ShortUrl shortUrl) {
		return new ShortUrlStatsResponse(
				shortUrl.getCode(),
				shortUrl.getOriginalUrl(),
				shortUrl.getVisitCount(),
				shortUrl.getCreatedAt(),
				shortUrl.getExpiresAt());
	}
}
