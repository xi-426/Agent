package com.yan.agent;

import com.yan.agent.chat.ChatRateLimitService;
import com.yan.agent.chat.TooManyChatRequestsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.security.max-chat-requests-per-minute=3")
class ChatRateLimitServiceTest {

    private static final Long USER_ID = 900_001L;

    @Autowired
    private ChatRateLimitService rateLimitService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanRateLimitKeys() {
        redisTemplate.delete("rate-limit:chat:" + USER_ID);
    }

    @Test
    void shouldRejectRequestAfterConfiguredLimit() {
        rateLimitService.checkAllowed(USER_ID);
        rateLimitService.checkAllowed(USER_ID);
        rateLimitService.checkAllowed(USER_ID);

        assertThatThrownBy(() -> rateLimitService.checkAllowed(USER_ID))
                .isInstanceOf(TooManyChatRequestsException.class)
                .hasMessage("请求过于频繁，请稍后再试");
    }
}
