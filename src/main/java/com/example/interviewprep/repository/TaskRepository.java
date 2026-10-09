package com.example.interviewprep.repository;

import com.example.interviewprep.enums.TaskStatus;
import com.example.interviewprep.model.Task;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

	List<Task> findByStatus(TaskStatus status);
}
