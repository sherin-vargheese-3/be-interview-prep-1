package com.example.interviewprep.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidUrlValidator.class)
public @interface ValidUrl {

	String message() default "must be a valid http or https URL";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
