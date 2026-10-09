package com.example.interviewprep.service;

import com.example.interviewprep.dto.PageResponse;
import com.example.interviewprep.dto.ProductFilter;
import com.example.interviewprep.dto.ProductRequest;
import com.example.interviewprep.dto.ProductResponse;
import com.example.interviewprep.exception.BadRequestException;
import com.example.interviewprep.exception.NotFoundException;
import com.example.interviewprep.model.Product;
import com.example.interviewprep.repository.ProductRepository;
import com.example.interviewprep.repository.ProductSpecifications;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

	public static final String CACHE_NAME = "products";

	private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "category", "price", "stock", "rating", "createdAt");

	private final ProductRepository repository;

	public ProductService(ProductRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public PageResponse<ProductResponse> list(ProductFilter filter, Pageable pageable) {
		validatePriceRange(filter);
		validateSort(pageable.getSort());
		return PageResponse.from(repository.findAll(toSpecification(filter), pageable).map(ProductResponse::from));
	}

	@Cacheable(value = CACHE_NAME, key = "#id")
	@Transactional(readOnly = true)
	public ProductResponse getById(Long id) {
		return ProductResponse.from(findProduct(id));
	}

	@CachePut(value = CACHE_NAME, key = "#id")
	@Transactional
	public ProductResponse update(Long id, ProductRequest request) {
		Product product = findProduct(id);
		product.update(request.name(), request.category(), request.price(), request.stock(), request.rating());
		return ProductResponse.from(product);
	}

	@CacheEvict(value = CACHE_NAME, key = "#id")
	@Transactional
	public void delete(Long id) {
		repository.delete(findProduct(id));
	}

	private Product findProduct(Long id) {
		return repository.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
	}

	private static Specification<Product> toSpecification(ProductFilter filter) {
		List<Specification<Product>> specifications = Stream.of(
						ProductSpecifications.categoryEquals(filter.category()),
						ProductSpecifications.priceAtLeast(filter.minPrice()),
						ProductSpecifications.priceAtMost(filter.maxPrice()),
						ProductSpecifications.inStockOnly(filter.inStock()),
						ProductSpecifications.nameContains(filter.q()))
				.filter(Objects::nonNull)
				.toList();
		return Specification.allOf(specifications);
	}

	private static void validatePriceRange(ProductFilter filter) {
		if (filter.minPrice() != null && filter.maxPrice() != null && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
			throw new BadRequestException("minPrice must not be greater than maxPrice");
		}
	}

	private static void validateSort(Sort sort) {
		for (Sort.Order order : sort) {
			if (!SORTABLE_FIELDS.contains(order.getProperty())) {
				throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'; sortable fields are " + SORTABLE_FIELDS.stream().sorted().toList());
			}
		}
	}
}
