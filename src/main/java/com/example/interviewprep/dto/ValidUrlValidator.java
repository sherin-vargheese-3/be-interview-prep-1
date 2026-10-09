package com.example.interviewprep.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public class ValidUrlValidator implements ConstraintValidator<ValidUrl, String> {

	private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		try {
			URI uri = new URI(value);
			return uri.getScheme() != null
					&& ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))
					&& uri.getHost() != null;
		} catch (URISyntaxException ex) {
			return false;
		}
	}
}
