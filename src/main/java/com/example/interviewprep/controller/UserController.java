package com.example.interviewprep.controller;

import com.example.interviewprep.dto.UserResponse;
import com.example.interviewprep.service.UserService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

	private final UserService service;

	public UserController(UserService service) {
		this.service = service;
	}

	@GetMapping("/users/me")
	public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return service.me(Long.valueOf(jwt.getSubject()));
	}

	@GetMapping("/admin/users")
	public List<UserResponse> listAll() {
		return service.listAll();
	}
}
