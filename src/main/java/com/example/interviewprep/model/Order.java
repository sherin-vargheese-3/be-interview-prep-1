package com.example.interviewprep.model;

import com.example.interviewprep.enums.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(
		name = "uk_orders_user_idempotency_key",
		columnNames = {"user_id", "idempotency_key"}))
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status;

	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;

	@Column(nullable = false, length = 64)
	private String requestHash;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("productId")
	private List<OrderItem> items = new ArrayList<>();

	protected Order() {
	}

	public Order(Long userId, String idempotencyKey, String requestHash) {
		this.userId = userId;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.status = OrderStatus.PLACED;
	}

	public void addItem(Long productId, int quantity) {
		items.add(new OrderItem(this, productId, quantity));
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public List<OrderItem> getItems() {
		return Collections.unmodifiableList(items);
	}
}
