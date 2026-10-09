package com.example.interviewprep.controller;

import com.example.interviewprep.dto.OrderPlacement;
import com.example.interviewprep.dto.OrderRequest;
import com.example.interviewprep.dto.OrderResponse;
import com.example.interviewprep.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

	public static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

	private final OrderService service;

	public OrderController(OrderService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<OrderResponse> place(
			@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
			@Valid @RequestBody OrderRequest request) {
		OrderPlacement placement = service.place(userId(jwt), idempotencyKey, request);
		if (placement.replayed()) {
			return ResponseEntity.ok().header(IDEMPOTENT_REPLAYED_HEADER, "true").body(placement.order());
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(placement.order());
	}

	@GetMapping("/{id}")
	public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return service.get(userId(jwt), id);
	}

	@PostMapping("/{id}/cancel")
	public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return service.cancel(userId(jwt), id);
	}

	private static Long userId(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}
}
