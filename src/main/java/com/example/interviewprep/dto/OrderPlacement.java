package com.example.interviewprep.dto;

public record OrderPlacement(OrderResponse order, boolean replayed) {

	public static OrderPlacement created(OrderResponse order) {
		return new OrderPlacement(order, false);
	}

	public static OrderPlacement replayed(OrderResponse order) {
		return new OrderPlacement(order, true);
	}
}
