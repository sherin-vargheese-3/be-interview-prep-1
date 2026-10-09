package com.example.interviewprep.service;

import com.example.interviewprep.dto.OrderItemRequest;
import com.example.interviewprep.dto.OrderRequest;
import com.example.interviewprep.dto.OrderResponse;
import com.example.interviewprep.exception.BadRequestException;
import com.example.interviewprep.exception.InsufficientStockException;
import com.example.interviewprep.exception.NotFoundException;
import com.example.interviewprep.model.Order;
import com.example.interviewprep.repository.OrderRepository;
import com.example.interviewprep.repository.ProductRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

	public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

	private final OrderRepository orderRepository;

	private final ProductRepository productRepository;

	public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
		this.orderRepository = orderRepository;
		this.productRepository = productRepository;
	}

	@Transactional
	public OrderResponse place(Long userId, String idempotencyKey, OrderRequest request) {
		validateIdempotencyKey(idempotencyKey);
		List<OrderItemRequest> lines = normalize(request.items());
		Order order = new Order(userId, idempotencyKey, hash(lines));
		lines.forEach(line -> order.addItem(line.productId(), line.quantity()));
		Order saved = orderRepository.saveAndFlush(order);
		lines.forEach(this::reserve);
		return OrderResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public OrderResponse get(Long userId, Long orderId) {
		return OrderResponse.from(findOwnedOrder(userId, orderId));
	}

	private void reserve(OrderItemRequest line) {
		if (productRepository.reserveStock(line.productId(), line.quantity()) == 0) {
			int available = productRepository.findStockById(line.productId())
					.orElseThrow(() -> new NotFoundException("Product " + line.productId() + " not found"));
			throw new InsufficientStockException(line.productId(), line.quantity(), available);
		}
	}

	private Order findOwnedOrder(Long userId, Long orderId) {
		return orderRepository.findByIdAndUserId(orderId, userId)
				.orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
	}

	private static void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new BadRequestException("Idempotency-Key header is required and must be 1 to "
					+ MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
		}
	}

	private static List<OrderItemRequest> normalize(List<OrderItemRequest> items) {
		Map<Long, Integer> quantities = items.stream()
				.collect(Collectors.toMap(OrderItemRequest::productId, OrderItemRequest::quantity, Integer::sum, TreeMap::new));
		return quantities.entrySet().stream()
				.map(entry -> new OrderItemRequest(entry.getKey(), entry.getValue()))
				.toList();
	}

	private static String hash(List<OrderItemRequest> lines) {
		String canonical = lines.stream()
				.map(line -> line.productId() + ":" + line.quantity())
				.collect(Collectors.joining(","));
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
