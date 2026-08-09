package com.yan.agent.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ChatRateLimitService {

    private static final String KEY_PREFIX = "rate-limit:chat:";
    private static final Duration KEY_TTL = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;
    private final long maxRequestsPerMinute;

    public ChatRateLimitService(
            StringRedisTemplate redisTemplate,
            @Value("${app.security.max-chat-requests-per-minute}") long maxRequestsPerMinute) {
        this.redisTemplate = redisTemplate;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public void checkAllowed(Long userId) {
        String key = KEY_PREFIX + userId;

        // Redis 的 INCR：如果 Key 不存在就从0变成1，
        // 已经存在就继续加1。该操作具有原子性。
        Long requestCount = redisTemplate
                .opsForValue()
                .increment(key);

        if (requestCount == null) {
            throw new IllegalStateException(
                    "Redis无法记录请求次数");
        }

        // 第一次创建这个 Key 时开启一分钟计时窗口。
        if (requestCount == 1) {
            redisTemplate.expire(
                    key,
                    KEY_TTL);
        }

        // 配置允许每分钟20次，第21次开始拒绝
        if (requestCount > maxRequestsPerMinute) {
            throw new TooManyChatRequestsException(
                    "请求过于频繁，请稍后再试");
        }
    }

}
