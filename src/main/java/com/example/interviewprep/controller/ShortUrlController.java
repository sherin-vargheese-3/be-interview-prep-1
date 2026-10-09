package com.example.interviewprep.controller;

import com.example.interviewprep.dto.ShortUrlRequest;
import com.example.interviewprep.dto.ShortUrlResponse;
import com.example.interviewprep.service.ShortUrlService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urls")
public class ShortUrlController {

	private final ShortUrlService service;

	public ShortUrlController(ShortUrlService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ShortUrlResponse create(@Valid @RequestBody ShortUrlRequest request) {
		return service.create(request);
	}
}
