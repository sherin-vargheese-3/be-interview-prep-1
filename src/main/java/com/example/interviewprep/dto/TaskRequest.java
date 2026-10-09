package com.example.interviewprep.dto;

import com.example.interviewprep.enums.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TaskRequest(
		@NotBlank @Size(max = 100) String title,
		@Size(max = 2000) String description,
		TaskStatus status,
		@FutureOrPresent LocalDate dueDate) {
}
