package com.yan.agent;

import com.yan.agent.chat.ChatSession;
import com.yan.agent.chat.ChatSessionNotFoundException;
import com.yan.agent.chat.ChatSessionService;
import com.yan.agent.user.AppUser;
import com.yan.agent.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ChatSessionOwnershipTest {

    @Autowired
    private ChatSessionService sessionService;

    @Autowired
    private AppUserRepository userRepository;

    @Test
    void shouldAllowOwnerAndRejectAnotherUser() {
        String uniqueSuffix = UUID.randomUUID().toString();
        AppUser owner = userRepository.save(new AppUser(
                "owner-" + uniqueSuffix + "@example.com",
                "会话所有者",
                "test-password-hash"));
        AppUser anotherUser = userRepository.save(new AppUser(
                "another-" + uniqueSuffix + "@example.com",
                "其他用户",
                "test-password-hash"));

        ChatSession session = sessionService.create(
                owner.getId(),
                "归属隔离测试");

        ChatSession ownedSession = sessionService.requireOwnedBy(
                session.getId(),
                owner.getId());

        assertThat(ownedSession.getId()).isEqualTo(session.getId());
        assertThat(sessionService.findOwnedBy(owner.getId()))
                .extracting(ChatSession::getId)
                .containsExactly(session.getId());
        assertThat(sessionService.findOwnedBy(anotherUser.getId()))
                .isEmpty();

        assertThatThrownBy(() -> sessionService.requireOwnedBy(
                session.getId(),
                anotherUser.getId()))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }
}
