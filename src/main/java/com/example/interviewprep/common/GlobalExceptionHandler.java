package com.example.interviewprep.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
		return respond(ApiError.of(ex.getStatus(), ex.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unexpected error on {}", request.getRequestURI(), ex);
		return respond(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request.getRequestURI()));
	}

	private ResponseEntity<ApiError> respond(ApiError error) {
		return ResponseEntity.status(error.status()).body(error);
	}
}
