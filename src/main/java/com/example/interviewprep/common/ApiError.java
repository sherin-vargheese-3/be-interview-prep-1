package com.example.interviewprep.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
		Instant timestamp,
		int status,
		String error,
		String message,
		String path,
		List<FieldErrorDetail> fieldErrors) {

	public static ApiError of(HttpStatus status, String message, String path) {
		return of(status, message, path, List.of());
	}

	public static ApiError of(HttpStatus status, String message, String path, List<FieldErrorDetail> fieldErrors) {
		return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors);
	}

	public record FieldErrorDetail(String field, String message) {
	}
}
