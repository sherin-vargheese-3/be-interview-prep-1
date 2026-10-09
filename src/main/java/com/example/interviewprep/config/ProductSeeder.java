package com.example.interviewprep.config;

import com.example.interviewprep.model.Product;
import com.example.interviewprep.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ProductSeeder implements ApplicationRunner {

	private static final int SEED_COUNT = 100;

	private static final Logger log = LoggerFactory.getLogger(ProductSeeder.class);
	private static final List<String> CATEGORIES = List.of("Electronics", "Books", "Home", "Sports", "Toys");
	private static final List<String> ADJECTIVES = List.of("Classic", "Compact", "Deluxe", "Eco", "Smart", "Ultra", "Vintage");
	private static final List<String> NOUNS = List.of("Lamp", "Speaker", "Notebook", "Backpack", "Puzzle", "Bottle", "Headphones", "Chair");

	private final ProductRepository repository;

	public ProductSeeder(ProductRepository repository) {
		this.repository = repository;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (repository.count() > 0) {
			return;
		}
		List<Product> products = IntStream.rangeClosed(1, SEED_COUNT).mapToObj(ProductSeeder::seedProduct).toList();
		repository.saveAll(products);
		log.info("Seeded {} products", products.size());
	}

	private static Product seedProduct(int index) {
		String name = ADJECTIVES.get(index % ADJECTIVES.size()) + " " + NOUNS.get(index % NOUNS.size()) + " " + index;
		String category = CATEGORIES.get(index % CATEGORIES.size());
		BigDecimal price = BigDecimal.valueOf((index * 37L) % 500 + 5).add(new BigDecimal("0.99"));
		int stock = index % 7 == 0 ? 0 : (index * 13) % 50 + 1;
		double rating = ((index * 7) % 51) / 10.0;
		return new Product(name, category, price, stock, rating);
	}
}
