package com.example.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.interviewprep.dto.ProductRequest;
import com.example.interviewprep.dto.ProductResponse;
import com.example.interviewprep.exception.NotFoundException;
import com.example.interviewprep.model.Product;
import com.example.interviewprep.repository.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class ProductServiceCacheTest {

	private static final long MISSING_ID = 999_999L;

	@MockitoSpyBean
	private ProductRepository repository;

	@Autowired
	private ProductService service;

	@Autowired
	private CacheManager cacheManager;

	private Long productId;

	@BeforeEach
	void setUp() {
		cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
		productId = repository.save(new Product("Cached Kettle", "Home", new BigDecimal("20.00"), 5, 4.5)).getId();
		reset(repository);
	}

	@AfterEach
	void removeProduct() {
		repository.deleteById(productId);
	}

	@Test
	void repeatedLookupHitsTheDatabaseOnce() {
		ProductResponse first = service.getById(productId);

		ProductResponse second = service.getById(productId);

		verify(repository, times(1)).findById(productId);
		assertThat(second).isEqualTo(first);
		assertThat(cacheManager.getCache(ProductService.CACHE_NAME).get(productId)).isNotNull();
	}

	@Test
	void missingProductIsNotCached() {
		assertThrows(NotFoundException.class, () -> service.getById(MISSING_ID));

		assertThrows(NotFoundException.class, () -> service.getById(MISSING_ID));

		verify(repository, times(2)).findById(MISSING_ID);
	}

	@Test
	void updateRefreshesTheCachedProduct() {
		service.getById(productId);
		ProductRequest request = new ProductRequest("Renamed Kettle", "Home", new BigDecimal("25.00"), 3, 4.0);

		service.update(productId, request);
		ProductResponse afterUpdate = service.getById(productId);

		assertThat(afterUpdate.name()).isEqualTo("Renamed Kettle");
		assertThat(afterUpdate.price()).isEqualByComparingTo("25.00");
		verify(repository, times(2)).findById(productId);
	}

	@Test
	void deleteEvictsTheCachedProduct() {
		service.getById(productId);

		service.delete(productId);

		assertThat(cacheManager.getCache(ProductService.CACHE_NAME).get(productId)).isNull();
		assertThrows(NotFoundException.class, () -> service.getById(productId));
	}
}
