package com.example.interviewprep.task;

import java.time.LocalDate;

public record TaskRequest(String title, String description, TaskStatus status, LocalDate dueDate) {
}
