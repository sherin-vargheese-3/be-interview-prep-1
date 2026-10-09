package com.example.interviewprep.repository;

import com.example.interviewprep.model.ShortUrl;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

	Optional<ShortUrl> findByCode(String code);

	boolean existsByCode(String code);
}
