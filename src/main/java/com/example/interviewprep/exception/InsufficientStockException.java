package com.example.interviewprep.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends ApiException {

	public InsufficientStockException(Long productId, int requested, int available) {
		super(HttpStatus.CONFLICT,
				"Insufficient stock for product " + productId + ": requested " + requested + ", available " + available);
	}
}
