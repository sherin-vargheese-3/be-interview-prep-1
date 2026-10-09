package com.example.interviewprep.repository;

import com.example.interviewprep.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
