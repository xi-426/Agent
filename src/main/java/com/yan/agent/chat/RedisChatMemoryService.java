package com.yan.agent.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class RedisChatMemoryService {

    private static final String KEY_PREFIX = "chat:memory:";

    private final StringRedisTemplate redisTemplate;
    private final int maxMessages;
    private final Duration ttl;

    public RedisChatMemoryService(
            StringRedisTemplate redisTemplate,
            @Value("${app.chat-memory.max-messages}") int maxMessages,
            @Value("${app.chat-memory.ttl-minutes}") long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.maxMessages = maxMessages;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public void append(
            Long sessionId,
            ChatMemoryMessage.Role role,
            String content) {
        String key = buildKey(sessionId);

        String storedMessage = serialize(
                role,
                content);

        // 从列表右边追加，新消息放在最后。
        redisTemplate.opsForList()
                .rightPush(key, storedMessage);

        // trim(key, -20, -1)：只保留最后20条。
        redisTemplate.opsForList()
                .trim(key, -maxMessages, -1);

        redisTemplate.expire(key, ttl);
    }

    public List<ChatMemoryMessage> getRecentMessages(Long sessionId) {
        String key = buildKey(sessionId);

        //range(key, 0, -1)表示读取第一条到最后一条，因此返回顺序仍是“旧消息 → 新消息”。
        List<String> storedMessages = redisTemplate.opsForList()
                .range(key, 0, -1);

        if (storedMessages == null
                || storedMessages.isEmpty()) {
            return List.of();
        }

        List<ChatMemoryMessage> messages = new ArrayList<>();

        //deserialize 也就是反序列化，是你这段代码里把Redis中存储的字符串格式的聊天消息数据，重新还原成内存里ChatMemoryMessage
        for (String storedMessage : storedMessages) {
            messages.add(
                    deserialize(storedMessage));
        }

        return messages;
    }

    public void clear(Long sessionId) {
        redisTemplate.delete(buildKey(sessionId));
    }

    private String buildKey(Long sessionId) {
        return KEY_PREFIX + sessionId;
    }

    private String serialize(
            ChatMemoryMessage.Role role,
            String content) {
        return role.name() + "\n" + content;
    }

    private ChatMemoryMessage deserialize(String storedMessage) {
        int separatorIndex = storedMessage.indexOf('\n');

        if (separatorIndex <= 0) {
            throw new IllegalStateException("Redis 中的会话消息格式不正确");
        }

        ChatMemoryMessage.Role role = ChatMemoryMessage.Role.valueOf(
                storedMessage.substring(0, separatorIndex));

        String content = storedMessage.substring(separatorIndex + 1);

        return new ChatMemoryMessage(role, content);
    }
}
