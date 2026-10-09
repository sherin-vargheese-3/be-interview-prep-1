package com.example.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.interviewprep.dto.RegisterRequest;
import com.example.interviewprep.enums.Role;
import com.example.interviewprep.exception.ConflictException;
import com.example.interviewprep.model.AppUser;
import com.example.interviewprep.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String PASSWORD = "dummy-pass-123";

	@Mock
	private AppUserRepository repository;

	@Mock
	private TokenService tokenService;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

	private AuthService service;

	@BeforeEach
	void setUp() {
		service = new AuthService(repository, passwordEncoder, tokenService);
	}

	@Test
	void concurrentRegistrationOfTheSameEmailIsAConflict() {
		when(repository.existsByEmail("race@example.com")).thenReturn(false);
		when(repository.saveAndFlush(any(AppUser.class))).thenThrow(new DataIntegrityViolationException("duplicate email"));

		ConflictException ex = assertThrows(ConflictException.class,
				() -> service.register(new RegisterRequest("race@example.com", PASSWORD)));

		assertThat(ex.getStatus().value()).isEqualTo(409);
	}

	@Test
	void existingAdminIsNotSeededAgain() {
		when(repository.existsByEmail("admin@example.com")).thenReturn(true);

		boolean created = service.createAdminIfAbsent(" Admin@Example.com ", PASSWORD);

		assertThat(created).isFalse();
		verify(repository, never()).saveAndFlush(any(AppUser.class));
	}

	@Test
	void missingAdminIsCreatedWithAHashedPassword() {
		when(repository.existsByEmail("admin@example.com")).thenReturn(false);
		when(repository.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

		boolean created = service.createAdminIfAbsent(" Admin@Example.com ", PASSWORD);

		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(repository).saveAndFlush(saved.capture());
		assertThat(created).isTrue();
		assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
		assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
		assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();
	}
}
