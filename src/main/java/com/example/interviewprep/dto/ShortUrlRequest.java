package com.example.interviewprep.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ShortUrlRequest(
		@NotBlank @Size(max = 2048) @ValidUrl String url,
		@Future Instant expiresAt) {
}
