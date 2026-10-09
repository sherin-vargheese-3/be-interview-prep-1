package com.example.interviewprep.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.interviewprep.model.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class ProductRepositoryStockTest {

	private static final long MISSING_ID = 999_999L;

	@Autowired
	private ProductRepository repository;

	@Test
	void reserveDecrementsWhenEnoughStockIsLeft() {
		Long id = saveProduct(5);

		int updated = repository.reserveStock(id, 3);

		assertThat(updated).isEqualTo(1);
		assertThat(repository.findStockById(id)).contains(2);
	}

	@Test
	void reserveRefusesMoreThanIsLeftAndLeavesStockAlone() {
		Long id = saveProduct(2);

		int updated = repository.reserveStock(id, 3);

		assertThat(updated).isZero();
		assertThat(repository.findStockById(id)).contains(2);
	}

	@Test
	void reserveCanTakeTheLastUnit() {
		Long id = saveProduct(1);

		int updated = repository.reserveStock(id, 1);

		assertThat(updated).isEqualTo(1);
		assertThat(repository.findStockById(id)).contains(0);
	}

	@Test
	void reserveOnAMissingProductUpdatesNothing() {
		int updated = repository.reserveStock(MISSING_ID, 1);

		assertThat(updated).isZero();
		assertThat(repository.findStockById(MISSING_ID)).isEmpty();
	}

	@Test
	void releaseAddsStockBack() {
		Long id = saveProduct(1);

		int updated = repository.releaseStock(id, 4);

		assertThat(updated).isEqualTo(1);
		assertThat(repository.findStockById(id)).contains(5);
	}

	@Test
	void databaseRejectsNegativeStock() {
		Product product = new Product("Negative Gadget", "Testware", new BigDecimal("1.00"), -1, 1.0);

		assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(product));
	}

	private Long saveProduct(int stock) {
		return repository.saveAndFlush(new Product("Stocked Gadget", "Testware", new BigDecimal("1.00"), stock, 1.0)).getId();
	}
}
