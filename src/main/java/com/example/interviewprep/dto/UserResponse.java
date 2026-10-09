package com.example.interviewprep.dto;

import com.example.interviewprep.enums.Role;
import com.example.interviewprep.model.AppUser;
import java.time.Instant;

public record UserResponse(Long id, String email, Role role, Instant createdAt) {

	public static UserResponse from(AppUser user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
	}
}
