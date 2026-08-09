package com.yan.agent;

import com.yan.agent.user.AppUser;
import com.yan.agent.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PersistenceSmokeTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldWriteAndReadPostgresAndRedis() {
        String uniqueValue = UUID.randomUUID().toString();
        String email = "day3-" + uniqueValue + "@example.com";

        AppUser user = new AppUser(
                email,
                "Day 3 Test User",
                "not-a-real-password-hash");

        AppUser savedUser = appUserRepository.saveAndFlush(user);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(appUserRepository.findByEmail(email)).isPresent();

        String redisKey = "test:day3:" + uniqueValue;

        try {
            stringRedisTemplate.opsForValue()
                    .set(redisKey, "redis-ok", Duration.ofSeconds(30));

            String redisValue = stringRedisTemplate.opsForValue()
                    .get(redisKey);

            assertThat(redisValue).isEqualTo("redis-ok");
        } finally {
            stringRedisTemplate.delete(redisKey);
        }
    }
}
