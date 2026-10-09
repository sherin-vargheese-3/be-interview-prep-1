package com.example.interviewprep.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration ttl) {

	public static final String ISSUER = "be-interview-prep";

	private static final int MIN_SECRET_BYTES = 32;

	public JwtProperties {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("app.jwt.secret is not set: provide JWT_SECRET with at least " + MIN_SECRET_BYTES + " bytes");
		}
		if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.jwt.secret is too short: JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes");
		}
		if (ttl == null || ttl.isZero() || ttl.isNegative()) {
			throw new IllegalStateException("app.jwt.ttl must be a positive duration");
		}
	}

	public SecretKey signingKey() {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Override
	public String toString() {
		return "JwtProperties[secret=***, ttl=" + ttl + "]";
	}
}
