package com.example.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.interviewprep.config.ProductSeeder;
import com.example.interviewprep.dto.ProductRequest;
import com.example.interviewprep.model.Product;
import com.example.interviewprep.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ProductControllerTest {

	private static final String TEST_CATEGORY = "Testware";
	private static final int SEEDED_PRODUCTS = 100;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ProductRepository repository;

	@Autowired
	private ProductSeeder seeder;

	@Autowired
	private CacheManager cacheManager;

	private final List<Product> created = new ArrayList<>();

	@BeforeEach
	void clearCaches() {
		cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
	}

	@AfterEach
	void removeTestProducts() {
		repository.deleteAll(created);
		created.clear();
	}

	@Test
	void seedingCreatesOneHundredProductsOnlyOnce() {
		seeder.run(new DefaultApplicationArguments());

		assertThat(repository.count()).isEqualTo(SEEDED_PRODUCTS);
	}

	@Test
	void combinedFiltersReturnOnlyMatchingProducts() throws Exception {
		Product cheapWidget = save("Epsilon WIDGET", TEST_CATEGORY, "30.00", 1);
		Product dearWidget = save("Zeta widget", TEST_CATEGORY, "99.99", 7);
		save("Alpha Widget", TEST_CATEGORY, "10.00", 5);
		save("Beta Widget", TEST_CATEGORY, "50.00", 0);
		save("Gamma Widget", TEST_CATEGORY, "200.00", 3);
		save("Delta Gizmo", TEST_CATEGORY, "40.00", 2);
		save("Other Widget", "Elsewhere", "40.00", 5);

		mockMvc.perform(get("/api/products")
						.param("category", "testware")
						.param("minPrice", "20")
						.param("maxPrice", "100")
						.param("inStock", "true")
						.param("q", "widget")
						.param("size", "1")
						.param("sort", "price,asc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(cheapWidget.getId()));
		mockMvc.perform(get("/api/products")
						.param("category", "testware")
						.param("minPrice", "20")
						.param("maxPrice", "100")
						.param("inStock", "true")
						.param("q", "widget")
						.param("sort", "price,desc"))
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].id").value(dearWidget.getId()))
				.andExpect(jsonPath("$.content[1].id").value(cheapWidget.getId()));
	}

	@Test
	void nameSearchTreatsWildcardsLiterally() throws Exception {
		save("100% Cotton Shirt", TEST_CATEGORY, "25.00", 4);
		save("Cotton Socks", TEST_CATEGORY, "5.00", 4);

		mockMvc.perform(get("/api/products").param("category", TEST_CATEGORY).param("q", "0% c"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].name").value("100% Cotton Shirt"));
	}

	@Test
	void pageSizeIsCappedAtOneHundred() throws Exception {
		mockMvc.perform(get("/api/products").param("size", "500"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100))
				.andExpect(jsonPath("$.content", hasSize(100)));
	}

	@Test
	void unknownSortFieldReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/products").param("sort", "secret,asc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value(startsWith("Cannot sort by 'secret'")))
				.andExpect(jsonPath("$.path").value("/api/products"));
	}

	@Test
	void minPriceAboveMaxPriceReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/products").param("minPrice", "50").param("maxPrice", "10"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("minPrice must not be greater than maxPrice"));
	}

	@Test
	void getReturnsTheProduct() throws Exception {
		Product product = save("Lookup Lamp", TEST_CATEGORY, "12.50", 3);

		mockMvc.perform(get("/api/products/{id}", product.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Lookup Lamp"))
				.andExpect(jsonPath("$.price").value(12.50))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void unknownProductReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/products/{id}", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
		mockMvc.perform(put("/api/products/{id}", 999_999).contentType(MediaType.APPLICATION_JSON).content(json(validRequest("Ghost"))))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/products/{id}", 999_999))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void updateIsVisibleOnTheNextLookup() throws Exception {
		Product product = save("Old Name", TEST_CATEGORY, "10.00", 1);
		mockMvc.perform(get("/api/products/{id}", product.getId())).andExpect(jsonPath("$.name").value("Old Name"));

		mockMvc.perform(put("/api/products/{id}", product.getId()).contentType(MediaType.APPLICATION_JSON).content(json(validRequest("New Name"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New Name"));

		mockMvc.perform(get("/api/products/{id}", product.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New Name"))
				.andExpect(jsonPath("$.stock").value(9));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void invalidUpdateReturnsFieldErrors() throws Exception {
		Product product = save("Valid", TEST_CATEGORY, "10.00", 1);
		String body = """
				{"name": "", "category": "Toys", "price": -1, "stock": -2, "rating": 6}
				""";

		mockMvc.perform(put("/api/products/{id}", product.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors", hasSize(4)));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void deletedProductIsNoLongerReturned() throws Exception {
		Product product = save("Doomed", TEST_CATEGORY, "10.00", 1);
		mockMvc.perform(get("/api/products/{id}", product.getId())).andExpect(status().isOk());

		mockMvc.perform(delete("/api/products/{id}", product.getId())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/products/{id}", product.getId())).andExpect(status().isNotFound());
	}

	private Product save(String name, String category, String price, int stock) {
		Product product = repository.save(new Product(name, category, new BigDecimal(price), stock, 4.0));
		created.add(product);
		return product;
	}

	private ProductRequest validRequest(String name) {
		return new ProductRequest(name, TEST_CATEGORY, new BigDecimal("15.00"), 9, 3.5);
	}

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}
}
