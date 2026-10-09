package com.example.interviewprep.controller;

import com.example.interviewprep.dto.OrderRequest;
import com.example.interviewprep.dto.OrderResponse;
import com.example.interviewprep.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

	private final OrderService service;

	public OrderController(OrderService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OrderResponse place(
			@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
			@Valid @RequestBody OrderRequest request) {
		return service.place(userId(jwt), idempotencyKey, request);
	}

	@GetMapping("/{id}")
	public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return service.get(userId(jwt), id);
	}

	private static Long userId(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}
}
