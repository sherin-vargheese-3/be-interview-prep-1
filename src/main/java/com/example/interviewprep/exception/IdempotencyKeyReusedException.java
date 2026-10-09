package com.example.interviewprep.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends ApiException {

	public IdempotencyKeyReusedException() {
		super(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency-Key was already used with a different request");
	}
}
