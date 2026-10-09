package com.example.interviewprep.repository;

import com.example.interviewprep.model.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Product p set p.stock = p.stock - :quantity where p.id = :id and p.stock >= :quantity")
	int reserveStock(@Param("id") Long id, @Param("quantity") int quantity);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Product p set p.stock = p.stock + :quantity where p.id = :id")
	int releaseStock(@Param("id") Long id, @Param("quantity") int quantity);

	@Query("select p.stock from Product p where p.id = :id")
	Optional<Integer> findStockById(@Param("id") Long id);
}
