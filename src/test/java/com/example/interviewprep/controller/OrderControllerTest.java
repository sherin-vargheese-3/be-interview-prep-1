package com.example.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.interviewprep.config.JwtProperties;
import com.example.interviewprep.model.Product;
import com.example.interviewprep.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

	private static final AtomicLong USER_IDS = new AtomicLong(1_000_000);

	private static final int TIMEOUT_SECONDS = 30;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CacheManager cacheManager;

	private final List<Long> createdProductIds = new ArrayList<>();

	@BeforeEach
	void clearCaches() {
		cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
	}

	@AfterEach
	void removeTestProducts() {
		productRepository.deleteAllById(createdProductIds);
		createdProductIds.clear();
	}

	@Test
	void fiftySimultaneousOrdersForTenUnitsSellExactlyTen() throws Exception {
		Long productId = saveProduct(10);
		String token = tokenFor(nextUserId());
		String body = itemsBody(productId, 1);

		List<MvcResult> results = runConcurrently(50, () -> placeOrder(token, UUID.randomUUID().toString(), body).andReturn());

		List<Integer> statuses = results.stream().map(result -> result.getResponse().getStatus()).toList();
		assertThat(Collections.frequency(statuses, 201)).isEqualTo(10);
		assertThat(Collections.frequency(statuses, 409)).isEqualTo(40);
		assertThat(stockOf(productId)).isZero();
		assertThat(ordersContaining(productId)).isEqualTo(10);
	}

	@Test
	void retryWithTheSameKeyReturnsTheOriginalOrder() throws Exception {
		Long productId = saveProduct(5);
		Long userId = nextUserId();
		String token = tokenFor(userId);
		String key = UUID.randomUUID().toString();
		MvcResult first = placeOrder(token, key, itemsBody(productId, 2)).andExpect(status().isCreated()).andReturn();

		ResultActions retry = placeOrder(token, key, itemsBody(productId, 2));

		retry.andExpect(status().isOk())
				.andExpect(header().string(OrderController.IDEMPOTENT_REPLAYED_HEADER, "true"))
				.andExpect(jsonPath("$.id").value(orderIdOf(first)));
		assertThat(retry.andReturn().getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
		assertThat(ordersFor(userId, key)).isEqualTo(1);
		assertThat(stockOf(productId)).isEqualTo(3);
	}

	@Test
	void simultaneousRetriesCreateOneOrder() throws Exception {
		Long productId = saveProduct(20);
		Long userId = nextUserId();
		String token = tokenFor(userId);
		String key = UUID.randomUUID().toString();
		String body = itemsBody(productId, 2);

		List<MvcResult> results = runConcurrently(10, () -> placeOrder(token, key, body).andReturn());

		List<Integer> statuses = results.stream().map(result -> result.getResponse().getStatus()).toList();
		List<Long> orderIds = new ArrayList<>();
		for (MvcResult result : results) {
			orderIds.add(orderIdOf(result));
		}
		assertThat(Collections.frequency(statuses, 201)).isEqualTo(1);
		assertThat(Collections.frequency(statuses, 200)).isEqualTo(9);
		assertThat(orderIds).containsOnly(orderIds.get(0));
		assertThat(ordersFor(userId, key)).isEqualTo(1);
		assertThat(stockOf(productId)).isEqualTo(18);
	}

	@Test
	void reusingAKeyWithADifferentPayloadIsRejected() throws Exception {
		Long productId = saveProduct(5);
		String token = tokenFor(nextUserId());
		String key = UUID.randomUUID().toString();
		placeOrder(token, key, itemsBody(productId, 1)).andExpect(status().isCreated());

		ResultActions reuse = placeOrder(token, key, itemsBody(productId, 2));

		reuse.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.message").value("Idempotency-Key was already used with a different request"));
		assertThat(stockOf(productId)).isEqualTo(4);
	}

	@Test
	void retryWithReorderedAndSplitLinesIsTheSameRequest() throws Exception {
		Long first = saveProduct(5);
		Long second = saveProduct(5);
		String token = tokenFor(nextUserId());
		String key = UUID.randomUUID().toString();
		String original = "{\"items\": [{\"productId\": %d, \"quantity\": 2}, {\"productId\": %d, \"quantity\": 1}]}"
				.formatted(first, second);
		String reshaped = "{\"items\": [{\"productId\": %d, \"quantity\": 1}, {\"productId\": %d, \"quantity\": 1}, {\"productId\": %d, \"quantity\": 1}]}"
				.formatted(second, first, first);
		placeOrder(token, key, original).andExpect(status().isCreated());

		ResultActions retry = placeOrder(token, key, reshaped);

		retry.andExpect(status().isOk());
		assertThat(stockOf(first)).isEqualTo(3);
		assertThat(stockOf(second)).isEqualTo(4);
	}

	@Test
	void theSameKeyFromAnotherUserPlacesTheirOwnOrder() throws Exception {
		Long productId = saveProduct(5);
		String key = UUID.randomUUID().toString();
		MvcResult mine = placeOrder(tokenFor(nextUserId()), key, itemsBody(productId, 1)).andExpect(status().isCreated()).andReturn();

		MvcResult theirs = placeOrder(tokenFor(nextUserId()), key, itemsBody(productId, 1)).andReturn();

		assertThat(theirs.getResponse().getStatus()).isEqualTo(201);
		assertThat(orderIdOf(theirs)).isNotEqualTo(orderIdOf(mine));
		assertThat(stockOf(productId)).isEqualTo(3);
	}

	@Test
	void failedOrderCanBeRetriedWithTheSameKey() throws Exception {
		Long productId = saveProduct(1);
		Long userId = nextUserId();
		String token = tokenFor(userId);
		String key = UUID.randomUUID().toString();
		placeOrder(token, key, itemsBody(productId, 2)).andExpect(status().isConflict());
		jdbcTemplate.update("update product set stock = stock + 1 where id = ?", productId);

		ResultActions retry = placeOrder(token, key, itemsBody(productId, 2));

		retry.andExpect(status().isCreated());
		assertThat(ordersFor(userId, key)).isEqualTo(1);
		assertThat(stockOf(productId)).isZero();
	}

	@Test
	void placedOrderIsCreatedAndReservesStock() throws Exception {
		Long productId = saveProduct(5);
		String token = tokenFor(nextUserId());

		ResultActions result = placeOrder(token, UUID.randomUUID().toString(), itemsBody(productId, 2));

		result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.status").value("PLACED"))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].productId").value(productId))
				.andExpect(jsonPath("$.items[0].quantity").value(2))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
		assertThat(stockOf(productId)).isEqualTo(3);
	}

	@Test
	void shortSecondItemRejectsTheWholeOrder() throws Exception {
		Long plentiful = saveProduct(10);
		Long scarce = saveProduct(1);
		Long userId = nextUserId();
		String key = UUID.randomUUID().toString();
		String body = "{\"items\": [{\"productId\": %d, \"quantity\": 2}, {\"productId\": %d, \"quantity\": 3}]}"
				.formatted(plentiful, scarce);

		ResultActions result = placeOrder(tokenFor(userId), key, body);

		result.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value("Insufficient stock for product " + scarce + ": requested 3, available 1"));
		assertThat(stockOf(plentiful)).isEqualTo(10);
		assertThat(stockOf(scarce)).isEqualTo(1);
		assertThat(ordersFor(userId, key)).isZero();
	}

	@Test
	void duplicateProductLinesAreMerged() throws Exception {
		Long productId = saveProduct(10);
		String body = "{\"items\": [{\"productId\": %d, \"quantity\": 2}, {\"productId\": %d, \"quantity\": 3}]}"
				.formatted(productId, productId);

		ResultActions result = placeOrder(tokenFor(nextUserId()), UUID.randomUUID().toString(), body);

		result.andExpect(status().isCreated())
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].quantity").value(5));
		assertThat(stockOf(productId)).isEqualTo(5);
	}

	@Test
	void unknownProductReturnsNotFoundAndKeepsNoOrder() throws Exception {
		Long userId = nextUserId();
		String key = UUID.randomUUID().toString();

		ResultActions result = placeOrder(tokenFor(userId), key, itemsBody(999_999L, 1));

		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Product 999999 not found"));
		assertThat(ordersFor(userId, key)).isZero();
	}

	@Test
	void missingIdempotencyKeyReturnsBadRequest() throws Exception {
		Long productId = saveProduct(5);

		ResultActions result = mockMvc.perform(post("/api/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(nextUserId()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(itemsBody(productId, 1)));

		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Idempotency-Key header is required and must be 1 to 100 characters"));
		assertThat(stockOf(productId)).isEqualTo(5);
	}

	@Test
	void overlongIdempotencyKeyReturnsBadRequest() throws Exception {
		Long productId = saveProduct(5);

		ResultActions result = placeOrder(tokenFor(nextUserId()), "k".repeat(101), itemsBody(productId, 1));

		result.andExpect(status().isBadRequest());
		assertThat(stockOf(productId)).isEqualTo(5);
	}

	@Test
	void zeroQuantityReturnsFieldErrors() throws Exception {
		Long productId = saveProduct(5);

		ResultActions result = placeOrder(tokenFor(nextUserId()), UUID.randomUUID().toString(), itemsBody(productId, 0));

		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("items[0].quantity"));
	}

	@Test
	void emptyItemsReturnFieldErrors() throws Exception {
		ResultActions result = placeOrder(tokenFor(nextUserId()), UUID.randomUUID().toString(), "{\"items\": []}");

		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("items"));
	}

	@Test
	void placingWithoutTokenReturnsUnauthorized() throws Exception {
		Long productId = saveProduct(5);

		ResultActions result = mockMvc.perform(post("/api/orders")
				.header(OrderController.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(itemsBody(productId, 1)));

		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
		assertThat(stockOf(productId)).isEqualTo(5);
	}

	@Test
	void ownerCanReadTheirOrder() throws Exception {
		Long productId = saveProduct(5);
		String token = tokenFor(nextUserId());
		long orderId = placedOrderId(token, productId, 1);

		ResultActions result = mockMvc.perform(get("/api/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.items[0].productId").value(productId));
	}

	@Test
	void anotherUsersOrderIsNotFound() throws Exception {
		Long productId = saveProduct(5);
		long orderId = placedOrderId(tokenFor(nextUserId()), productId, 1);
		String intruder = tokenFor(nextUserId());

		ResultActions result = mockMvc.perform(get("/api/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder));

		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Order " + orderId + " not found"));
	}

	@Test
	void cancelReturnsTheStock() throws Exception {
		Long productId = saveProduct(5);
		String token = tokenFor(nextUserId());
		long orderId = placedOrderId(token, productId, 2);

		ResultActions result = cancelOrder(token, orderId);

		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.items[0].quantity").value(2));
		assertThat(stockOf(productId)).isEqualTo(5);
	}

	@Test
	void cancellingTwiceReturnsTheStockOnce() throws Exception {
		Long productId = saveProduct(5);
		String token = tokenFor(nextUserId());
		long orderId = placedOrderId(token, productId, 2);
		cancelOrder(token, orderId).andExpect(status().isOk());

		ResultActions second = cancelOrder(token, orderId);

		second.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(stockOf(productId)).isEqualTo(5);
	}

	@Test
	void simultaneousCancelsReturnTheStockOnce() throws Exception {
		Long productId = saveProduct(5);
		String token = tokenFor(nextUserId());
		long orderId = placedOrderId(token, productId, 3);

		List<MvcResult> results = runConcurrently(10, () -> cancelOrder(token, orderId).andReturn());

		assertThat(results).allSatisfy(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
		assertThat(stockOf(productId)).isEqualTo(5);
	}

	@Test
	void anotherUserCannotCancelMyOrder() throws Exception {
		Long productId = saveProduct(5);
		String owner = tokenFor(nextUserId());
		long orderId = placedOrderId(owner, productId, 2);

		ResultActions result = cancelOrder(tokenFor(nextUserId()), orderId);

		result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Order " + orderId + " not found"));
		assertThat(stockOf(productId)).isEqualTo(3);
		mockMvc.perform(get("/api/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
				.andExpect(jsonPath("$.status").value("PLACED"));
	}

	@Test
	void cancellingAnUnknownOrderReturnsNotFound() throws Exception {
		ResultActions result = cancelOrder(tokenFor(nextUserId()), 999_999L);

		result.andExpect(status().isNotFound());
	}

	@Test
	void retryAfterCancelReplaysTheCancelledOrder() throws Exception {
		Long productId = saveProduct(5);
		String token = tokenFor(nextUserId());
		String key = UUID.randomUUID().toString();
		long orderId = orderIdOf(placeOrder(token, key, itemsBody(productId, 2)).andExpect(status().isCreated()).andReturn());
		cancelOrder(token, orderId).andExpect(status().isOk());

		ResultActions retry = placeOrder(token, key, itemsBody(productId, 2));

		retry.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(stockOf(productId)).isEqualTo(5);
	}

	private ResultActions cancelOrder(String token, long orderId) throws Exception {
		return mockMvc.perform(post("/api/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions placeOrder(String token, String idempotencyKey, String body) throws Exception {
		return mockMvc.perform(post("/api/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header(OrderController.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private long placedOrderId(String token, Long productId, int quantity) throws Exception {
		MvcResult result = placeOrder(token, UUID.randomUUID().toString(), itemsBody(productId, quantity))
				.andExpect(status().isCreated())
				.andReturn();
		return orderIdOf(result);
	}

	private long orderIdOf(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	private static String itemsBody(Long productId, int quantity) {
		return "{\"items\": [{\"productId\": %d, \"quantity\": %d}]}".formatted(productId, quantity);
	}

	private static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch startGate = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(executor.submit(() -> {
					startGate.await();
					return task.call();
				}));
			}
			startGate.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

	private Long saveProduct(int stock) {
		Product product = productRepository.save(
				new Product("Order Test " + UUID.randomUUID(), "OrderTest", new BigDecimal("9.99"), stock, 4.0));
		createdProductIds.add(product.getId());
		return product.getId();
	}

	private int stockOf(Long productId) {
		return jdbcTemplate.queryForObject("select stock from product where id = ?", Integer.class, productId);
	}

	private long ordersContaining(Long productId) {
		return jdbcTemplate.queryForObject(
				"select count(distinct order_id) from order_items where product_id = ?", Long.class, productId);
	}

	private long ordersFor(Long userId, String idempotencyKey) {
		return jdbcTemplate.queryForObject(
				"select count(*) from orders where user_id = ? and idempotency_key = ?", Long.class, userId, idempotencyKey);
	}

	private static Long nextUserId() {
		return USER_IDS.incrementAndGet();
	}

	private String tokenFor(Long userId) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(JwtProperties.ISSUER)
				.subject(String.valueOf(userId))
				.issuedAt(now)
				.expiresAt(now.plus(5, ChronoUnit.MINUTES))
				.claim("roles", List.of("USER"))
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}
}
