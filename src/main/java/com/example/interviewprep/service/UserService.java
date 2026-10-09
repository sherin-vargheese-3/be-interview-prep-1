package com.example.interviewprep.service;

import com.example.interviewprep.dto.UserResponse;
import com.example.interviewprep.exception.NotFoundException;
import com.example.interviewprep.repository.AppUserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private final AppUserRepository repository;

	public UserService(AppUserRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public UserResponse me(Long userId) {
		return repository.findById(userId)
				.map(UserResponse::from)
				.orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
	}

	@Transactional(readOnly = true)
	public List<UserResponse> listAll() {
		return repository.findAll(Sort.by("id")).stream().map(UserResponse::from).toList();
	}
}
