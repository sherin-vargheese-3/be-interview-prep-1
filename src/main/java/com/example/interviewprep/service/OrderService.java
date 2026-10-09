package com.example.interviewprep.service;

import com.example.interviewprep.dto.OrderItemRequest;
import com.example.interviewprep.dto.OrderPlacement;
import com.example.interviewprep.dto.OrderRequest;
import com.example.interviewprep.dto.OrderResponse;
import com.example.interviewprep.enums.OrderStatus;
import com.example.interviewprep.exception.BadRequestException;
import com.example.interviewprep.exception.IdempotencyKeyReusedException;
import com.example.interviewprep.exception.InsufficientStockException;
import com.example.interviewprep.exception.NotFoundException;
import com.example.interviewprep.model.Order;
import com.example.interviewprep.model.OrderItem;
import com.example.interviewprep.repository.OrderRepository;
import com.example.interviewprep.repository.ProductRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {

	public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

	private final OrderRepository orderRepository;

	private final ProductRepository productRepository;

	private final CacheManager cacheManager;

	private final TransactionTemplate writeTransaction;

	private final TransactionTemplate readTransaction;

	public OrderService(
			OrderRepository orderRepository,
			ProductRepository productRepository,
			CacheManager cacheManager,
			PlatformTransactionManager transactionManager) {
		this.orderRepository = orderRepository;
		this.productRepository = productRepository;
		this.cacheManager = cacheManager;
		this.writeTransaction = new TransactionTemplate(transactionManager);
		this.readTransaction = new TransactionTemplate(transactionManager);
		this.readTransaction.setReadOnly(true);
	}

	public OrderPlacement place(Long userId, String idempotencyKey, OrderRequest request) {
		validateIdempotencyKey(idempotencyKey);
		List<OrderItemRequest> lines = normalize(request.items());
		String requestHash = hash(lines);
		return findPrevious(userId, idempotencyKey, requestHash)
				.map(OrderPlacement::replayed)
				.orElseGet(() -> create(userId, idempotencyKey, requestHash, lines));
	}

	@Transactional(readOnly = true)
	public OrderResponse get(Long userId, Long orderId) {
		return OrderResponse.from(findOwnedOrder(userId, orderId));
	}

	@Transactional
	public OrderResponse cancel(Long userId, Long orderId) {
		boolean cancelledNow = orderRepository.updateStatus(orderId, userId, OrderStatus.PLACED, OrderStatus.CANCELLED) == 1;
		Order order = findOwnedOrder(userId, orderId);
		OrderResponse response = OrderResponse.from(order);
		if (cancelledNow) {
			List<OrderItem> items = order.getItems().stream()
					.sorted(Comparator.comparing(OrderItem::getProductId))
					.toList();
			items.forEach(item -> productRepository.releaseStock(item.getProductId(), item.getQuantity()));
			evictProductsAfterCommit(items.stream().map(OrderItem::getProductId).toList());
		}
		return response;
	}

	private OrderPlacement create(Long userId, String idempotencyKey, String requestHash, List<OrderItemRequest> lines) {
		try {
			return OrderPlacement.created(writeTransaction.execute(status -> reserveAll(userId, idempotencyKey, requestHash, lines)));
		} catch (DataIntegrityViolationException ex) {
			return findPrevious(userId, idempotencyKey, requestHash)
					.map(OrderPlacement::replayed)
					.orElseThrow(() -> ex);
		}
	}

	private OrderResponse reserveAll(Long userId, String idempotencyKey, String requestHash, List<OrderItemRequest> lines) {
		Order order = new Order(userId, idempotencyKey, requestHash);
		lines.forEach(line -> order.addItem(line.productId(), line.quantity()));
		Order saved = orderRepository.saveAndFlush(order);
		lines.forEach(this::reserve);
		evictProductsAfterCommit(lines.stream().map(OrderItemRequest::productId).toList());
		return OrderResponse.from(saved);
	}

	private void evictProductsAfterCommit(Collection<Long> productIds) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				Cache cache = cacheManager.getCache(ProductService.CACHE_NAME);
				if (cache != null) {
					productIds.forEach(cache::evict);
				}
			}
		});
	}

	private Optional<OrderResponse> findPrevious(Long userId, String idempotencyKey, String requestHash) {
		return readTransaction.execute(status -> orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
				.map(order -> requireSameRequest(order, requestHash))
				.map(OrderResponse::from));
	}

	private static Order requireSameRequest(Order order, String requestHash) {
		if (!order.getRequestHash().equals(requestHash)) {
			throw new IdempotencyKeyReusedException();
		}
		return order;
	}

	private void reserve(OrderItemRequest line) {
		if (productRepository.reserveStock(line.productId(), line.quantity()) == 0) {
			int available = productRepository.findStockById(line.productId())
					.orElseThrow(() -> new NotFoundException("Product " + line.productId() + " not found"));
			throw new InsufficientStockException(line.productId(), line.quantity(), available);
		}
	}

	private Order findOwnedOrder(Long userId, Long orderId) {
		return orderRepository.findByIdAndUserId(orderId, userId)
				.orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
	}

	private static void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new BadRequestException("Idempotency-Key header is required and must be 1 to "
					+ MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
		}
	}

	private static List<OrderItemRequest> normalize(List<OrderItemRequest> items) {
		Map<Long, Integer> quantities = items.stream()
				.collect(Collectors.toMap(OrderItemRequest::productId, OrderItemRequest::quantity, Integer::sum, TreeMap::new));
		return quantities.entrySet().stream()
				.map(entry -> new OrderItemRequest(entry.getKey(), entry.getValue()))
				.toList();
	}

	private static String hash(List<OrderItemRequest> lines) {
		String canonical = lines.stream()
				.map(line -> line.productId() + ":" + line.quantity())
				.collect(Collectors.joining(","));
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
