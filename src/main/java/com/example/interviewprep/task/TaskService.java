package com.example.interviewprep.task;

import com.example.interviewprep.common.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

	private final TaskRepository repository;

	public TaskService(TaskRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public TaskResponse create(TaskRequest request) {
		Task task = new Task(request.title(), request.description(), request.status(), request.dueDate());
		return TaskResponse.from(repository.save(task));
	}

	@Transactional(readOnly = true)
	public TaskResponse get(Long id) {
		return TaskResponse.from(findTask(id));
	}

	@Transactional(readOnly = true)
	public List<TaskResponse> list(TaskStatus status) {
		List<Task> tasks = status == null ? repository.findAll() : repository.findByStatus(status);
		return tasks.stream().map(TaskResponse::from).toList();
	}

	@Transactional
	public TaskResponse update(Long id, TaskRequest request) {
		Task task = findTask(id);
		task.update(request.title(), request.description(), request.status(), request.dueDate());
		return TaskResponse.from(task);
	}

	@Transactional
	public void delete(Long id) {
		repository.delete(findTask(id));
	}

	private Task findTask(Long id) {
		return repository.findById(id).orElseThrow(() -> new NotFoundException("Task " + id + " not found"));
	}
}
