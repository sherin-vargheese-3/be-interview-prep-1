package com.example.interviewprep.service;

import com.example.interviewprep.dto.LoginRequest;
import com.example.interviewprep.dto.RegisterRequest;
import com.example.interviewprep.dto.TokenResponse;
import com.example.interviewprep.dto.UserResponse;
import com.example.interviewprep.enums.Role;
import com.example.interviewprep.exception.ConflictException;
import com.example.interviewprep.exception.UnauthorizedException;
import com.example.interviewprep.model.AppUser;
import com.example.interviewprep.repository.AppUserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private static final String EMAIL_TAKEN = "Email is already registered";

	private static final String INVALID_CREDENTIALS = "Invalid email or password";

	private final AppUserRepository repository;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokenService;

	private final String unknownUserHash;

	public AuthService(AppUserRepository repository, PasswordEncoder passwordEncoder, TokenService tokenService) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		AppUser user = createUser(normalizeEmail(request.email()), request.password(), Role.USER);
		log.info("Registered user {}", user.getId());
		return UserResponse.from(user);
	}

	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest request) {
		Optional<AppUser> user = repository.findByEmail(normalizeEmail(request.email()));
		String storedHash = user.map(AppUser::getPasswordHash).orElse(unknownUserHash);
		boolean passwordMatches = passwordEncoder.matches(request.password(), storedHash);
		return user.filter(found -> passwordMatches)
				.map(tokenService::issue)
				.orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
	}

	@Transactional
	public boolean createAdminIfAbsent(String email, String rawPassword) {
		String normalizedEmail = normalizeEmail(email);
		if (repository.existsByEmail(normalizedEmail)) {
			return false;
		}
		createUser(normalizedEmail, rawPassword, Role.ADMIN);
		return true;
	}

	private AppUser createUser(String email, String rawPassword, Role role) {
		if (repository.existsByEmail(email)) {
			throw new ConflictException(EMAIL_TAKEN);
		}
		try {
			return repository.saveAndFlush(new AppUser(email, passwordEncoder.encode(rawPassword), role));
		} catch (DataIntegrityViolationException ex) {
			throw new ConflictException(EMAIL_TAKEN);
		}
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
