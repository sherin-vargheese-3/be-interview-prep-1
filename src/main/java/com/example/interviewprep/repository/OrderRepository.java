package com.example.interviewprep.repository;

import com.example.interviewprep.model.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

	@EntityGraph(attributePaths = "items")
	Optional<Order> findByIdAndUserId(Long id, Long userId);
}
