package com.example.interviewprep.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
public class ShortUrl {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 8)
	private String code;

	@Column(nullable = false, length = 2048)
	private String originalUrl;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant expiresAt;

	@Column(nullable = false)
	private long visitCount;

	protected ShortUrl() {
	}

	public ShortUrl(String code, String originalUrl, Instant expiresAt) {
		this.code = code;
		this.originalUrl = originalUrl;
		this.expiresAt = expiresAt;
	}

	public boolean isExpiredAt(Instant moment) {
		return expiresAt != null && !expiresAt.isAfter(moment);
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getOriginalUrl() {
		return originalUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public long getVisitCount() {
		return visitCount;
	}
}
