package com.example.interviewprep.repository;

import com.example.interviewprep.model.Product;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

	private static final char LIKE_ESCAPE = '\\';

	private ProductSpecifications() {
	}

	public static Specification<Product> categoryEquals(String category) {
		if (category == null || category.isBlank()) {
			return null;
		}
		String normalized = category.trim().toLowerCase(Locale.ROOT);
		return (root, query, cb) -> cb.equal(cb.lower(root.get("category")), normalized);
	}

	public static Specification<Product> priceAtLeast(BigDecimal minPrice) {
		if (minPrice == null) {
			return null;
		}
		return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
	}

	public static Specification<Product> priceAtMost(BigDecimal maxPrice) {
		if (maxPrice == null) {
			return null;
		}
		return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
	}

	public static Specification<Product> inStockOnly(Boolean inStock) {
		if (!Boolean.TRUE.equals(inStock)) {
			return null;
		}
		return (root, query, cb) -> cb.greaterThan(root.get("stock"), 0);
	}

	public static Specification<Product> nameContains(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String pattern = "%" + escapeLikeWildcards(text.trim().toLowerCase(Locale.ROOT)) + "%";
		return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE);
	}

	private static String escapeLikeWildcards(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
