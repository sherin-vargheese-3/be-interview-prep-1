package com.example.interviewprep.service;

import com.example.interviewprep.config.JwtProperties;
import com.example.interviewprep.dto.TokenResponse;
import com.example.interviewprep.model.AppUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

	private static final String TOKEN_TYPE = "Bearer";

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	private final Clock clock;

	public TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	public TokenResponse issue(AppUser user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(JwtProperties.ISSUER)
				.subject(String.valueOf(user.getId()))
				.issuedAt(now)
				.expiresAt(now.plus(properties.ttl()))
				.claim("email", user.getEmail())
				.claim("roles", List.of(user.getRole().name()))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new TokenResponse(token, TOKEN_TYPE, properties.ttl().toSeconds());
	}
}
