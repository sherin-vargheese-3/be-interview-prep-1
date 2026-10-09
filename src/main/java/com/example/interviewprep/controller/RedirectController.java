package com.example.interviewprep.controller;

import com.example.interviewprep.service.ShortUrlService;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

	private final ShortUrlService service;

	public RedirectController(ShortUrlService service) {
		this.service = service;
	}

	@GetMapping("/{code:[A-Za-z0-9]{7}}")
	public ResponseEntity<Void> redirect(@PathVariable String code) {
		URI target = URI.create(service.resolveAndCountVisit(code));
		return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
	}
}
