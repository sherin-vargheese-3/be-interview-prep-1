package com.example.interviewprep.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.interviewprep.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

	private static final String PASSWORD = "dummy-pass-123";

	@Mock
	private AuthService authService;

	@Test
	void blankCredentialsSeedNothing() {
		AdminSeeder seeder = new AdminSeeder(new AdminProperties("", ""), authService);

		seeder.run(new DefaultApplicationArguments());

		verifyNoInteractions(authService);
	}

	@Test
	void missingPasswordSeedsNothing() {
		AdminSeeder seeder = new AdminSeeder(new AdminProperties("admin@example.com", " "), authService);

		seeder.run(new DefaultApplicationArguments());

		verifyNoInteractions(authService);
	}

	@Test
	void configuredCredentialsCreateTheAdmin() {
		AdminSeeder seeder = new AdminSeeder(new AdminProperties("admin@example.com", PASSWORD), authService);

		seeder.run(new DefaultApplicationArguments());

		verify(authService).createAdminIfAbsent("admin@example.com", PASSWORD);
	}
}
