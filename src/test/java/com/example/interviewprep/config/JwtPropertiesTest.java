package com.example.interviewprep.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtPropertiesTest {

	private static final Duration TTL = Duration.ofMinutes(15);

	private static final String LONG_ENOUGH = "fake-signing-key-with-thirty-two-bytes!!";

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void missingSecretFailsFast(String secret) {
		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtProperties(secret, TTL));

		assertThat(ex.getMessage()).contains("JWT_SECRET");
	}

	@Test
	void secretShorterThan32BytesFailsWithoutEchoingIt() {
		String shortSecret = "fake-short-key-31-bytes-long-xx";

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtProperties(shortSecret, TTL));

		assertThat(ex.getMessage()).contains("at least 32 bytes").doesNotContain(shortSecret);
	}

	@Test
	void nonPositiveTtlFailsFast() {
		assertThrows(IllegalStateException.class, () -> new JwtProperties(LONG_ENOUGH, Duration.ZERO));
	}

	@Test
	void validSettingsBuildAnHmacKeyAndHideTheSecret() {
		JwtProperties properties = new JwtProperties(LONG_ENOUGH, TTL);

		String rendered = properties.toString();

		assertThat(properties.signingKey().getAlgorithm()).isEqualTo("HmacSHA256");
		assertThat(rendered).doesNotContain(LONG_ENOUGH);
	}
}
