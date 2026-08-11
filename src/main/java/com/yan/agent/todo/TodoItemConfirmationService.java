package com.yan.agent.todo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

@Service
public class TodoItemConfirmationService {

    private static final String KEY_PREFIX = "todo-item:pending:";

    private final StringRedisTemplate redisTemplate;
    private final TodoItemRepository todoItemRepository;
    private final Duration confirmationTtl;

    public TodoItemConfirmationService(
            StringRedisTemplate redisTemplate,
            TodoItemRepository todoItemRepository,
            @Value("${app.todo-item.confirmation-ttl-minutes}") long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.todoItemRepository = todoItemRepository;
        this.confirmationTtl = Duration.ofMinutes(ttlMinutes);
    }

    public String prepare(
            Long userId,
            Long sessionId,
            String title,
            String description,
            TodoItem.Priority priority) {
        String key = buildKey(userId, sessionId);
        redisTemplate.opsForHash().putAll(
                key,
                Map.of(
                        "title", title,
                        "description", description,
                        "priority", priority.name()));
        redisTemplate.expire(key, confirmationTtl);

        return """
                准备创建待办事项：
                标题：%s
                描述：%s
                优先级：%s
                请回复“确认创建待办”完成创建，或回复“取消创建待办”取消。
                """.formatted(title, description, priority);
    }

    @Transactional
    public String confirm(Long userId, Long sessionId) {
        String key = buildKey(userId, sessionId);
        Map<Object, Object> pending = redisTemplate.opsForHash().entries(key);

        if (pending.isEmpty()) {
            return "当前没有等待确认的待办事项，可能已经取消或超过10分钟。";
        }

        String title = String.valueOf(pending.get("title"));
        String description = String.valueOf(pending.get("description"));
        TodoItem.Priority priority = TodoItem.Priority.valueOf(
                String.valueOf(pending.get("priority")));

        TodoItem savedTodoItem = todoItemRepository.saveAndFlush(
                new TodoItem(userId, title, description, priority));

        // 数据库确认写入成功后再删除 Redis 草稿，避免写库失败时草稿提前丢失。
        redisTemplate.delete(key);

        return "待办事项创建成功，待办编号：" + savedTodoItem.getId();
    }

    public void cancel(Long userId, Long sessionId) {
        redisTemplate.delete(buildKey(userId, sessionId));
    }

    public boolean hasPending(Long userId, Long sessionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(userId, sessionId)));
    }

    private String buildKey(Long userId, Long sessionId) {
        return KEY_PREFIX + userId + ":" + sessionId;
    }
}
