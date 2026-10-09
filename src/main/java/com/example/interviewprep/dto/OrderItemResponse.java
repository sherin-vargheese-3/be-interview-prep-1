package com.example.interviewprep.dto;

import com.example.interviewprep.model.OrderItem;

public record OrderItemResponse(Long productId, int quantity) {

	public static OrderItemResponse from(OrderItem item) {
		return new OrderItemResponse(item.getProductId(), item.getQuantity());
	}
}
