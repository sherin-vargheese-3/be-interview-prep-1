package com.example.interviewprep.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
		@NotNull Long productId,
		@NotNull @Min(1) @Max(1000) Integer quantity) {
}
