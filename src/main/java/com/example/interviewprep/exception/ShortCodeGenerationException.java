package com.example.interviewprep.exception;

import org.springframework.http.HttpStatus;

public class ShortCodeGenerationException extends ApiException {

	public ShortCodeGenerationException(String message) {
		super(HttpStatus.SERVICE_UNAVAILABLE, message);
	}
}
