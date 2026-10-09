package com.example.interviewprep.config;

import com.example.interviewprep.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

	private final AdminProperties properties;

	private final AuthService authService;

	public AdminSeeder(AdminProperties properties, AuthService authService) {
		this.properties = properties;
		this.authService = authService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.isConfigured()) {
			log.info("No admin seeded: ADMIN_EMAIL and ADMIN_PASSWORD are not both set");
			return;
		}
		if (authService.createAdminIfAbsent(properties.email(), properties.password())) {
			log.info("Seeded admin user");
		} else {
			log.info("Admin user already exists");
		}
	}
}
