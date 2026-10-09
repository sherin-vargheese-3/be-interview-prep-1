package com.example.interviewprep.repository;

import com.example.interviewprep.enums.OrderStatus;
import com.example.interviewprep.model.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

	@EntityGraph(attributePaths = "items")
	Optional<Order> findByIdAndUserId(Long id, Long userId);

	@EntityGraph(attributePaths = "items")
	Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Order o set o.status = :target where o.id = :id and o.userId = :userId and o.status = :expected")
	int updateStatus(
			@Param("id") Long id,
			@Param("userId") Long userId,
			@Param("expected") OrderStatus expected,
			@Param("target") OrderStatus target);
}
