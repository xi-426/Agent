package com.yan.agent.todo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

    List<TodoItem> findByUserIdOrderByIdDesc(Long userId);

    List<TodoItem> findByUserIdAndStatusOrderByIdDesc(
            Long userId,
            TodoItem.Status status);

    long countByUserIdAndStatus(Long userId, TodoItem.Status status);
}
