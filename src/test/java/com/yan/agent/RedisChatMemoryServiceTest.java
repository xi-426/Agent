package com.yan.agent;

import com.yan.agent.chat.ChatMemoryMessage;
import com.yan.agent.chat.RedisChatMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisChatMemoryServiceTest {

    private static final Long TEST_SESSION_ID = 9_999_999L;
    private static final String TEST_KEY = "chat:memory:" + TEST_SESSION_ID;

    @Autowired
    private RedisChatMemoryService memoryService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        memoryService.clear(TEST_SESSION_ID);
    }

    @Test
    void shouldAppendAndReadMessagesInOrder() {
        memoryService.append(
                TEST_SESSION_ID,
                ChatMemoryMessage.Role.USER,
                "我叫小明");

        memoryService.append(
                TEST_SESSION_ID,
                ChatMemoryMessage.Role.ASSISTANT,
                "你好，小明");

        List<ChatMemoryMessage> messages = memoryService.getRecentMessages(
                TEST_SESSION_ID);

        assertThat(messages).hasSize(2);

        assertThat(messages.get(0).getRole())
                .isEqualTo(ChatMemoryMessage.Role.USER);

        assertThat(messages.get(0).getContent())
                .isEqualTo("我叫小明");

        assertThat(messages.get(1).getRole())
                .isEqualTo(ChatMemoryMessage.Role.ASSISTANT);

        assertThat(messages.get(1).getContent())
                .isEqualTo("你好，小明");
    }

    @Test
    void shouldKeepOnlyLatestMessagesAndSetTtl() {
        for (int index = 0; index < 22; index++) {
            memoryService.append(
                    TEST_SESSION_ID,
                    ChatMemoryMessage.Role.USER,
                    "消息" + index);
        }

        List<ChatMemoryMessage> messages = memoryService.getRecentMessages(
                TEST_SESSION_ID);

        assertThat(messages).hasSize(20);

        assertThat(messages.get(0).getContent())
                .isEqualTo("消息2");

        assertThat(messages.get(19).getContent())
                .isEqualTo("消息21");

        Long ttlSeconds = redisTemplate.getExpire(
                TEST_KEY,
                TimeUnit.SECONDS);

        assertThat(ttlSeconds)
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(86_400L);
    }
}
