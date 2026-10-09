package com.example.interviewprep.dto;

import com.example.interviewprep.enums.OrderStatus;
import com.example.interviewprep.model.Order;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record OrderResponse(
		Long id,
		OrderStatus status,
		List<OrderItemResponse> items,
		Instant createdAt) {

	public static OrderResponse from(Order order) {
		List<OrderItemResponse> items = order.getItems().stream()
				.map(OrderItemResponse::from)
				.sorted(Comparator.comparing(OrderItemResponse::productId))
				.toList();
		return new OrderResponse(order.getId(), order.getStatus(), items, order.getCreatedAt());
	}
}
