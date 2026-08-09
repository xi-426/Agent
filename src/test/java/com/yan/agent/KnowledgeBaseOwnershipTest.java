package com.yan.agent;

import com.yan.agent.document.KnowledgeBase;
import com.yan.agent.document.KnowledgeBaseNotFoundException;
import com.yan.agent.document.KnowledgeBaseService;
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
class KnowledgeBaseOwnershipTest {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Test
    void shouldAllowOwnerAndRejectAnotherUser() {
        String suffix = UUID.randomUUID().toString();

        AppUser owner = userRepository.save(new AppUser(
                "kb-owner-" + suffix + "@example.com",
                "知识库所有者",
                "test-password-hash"));

        AppUser anotherUser = userRepository.save(new AppUser(
                "kb-another-" + suffix + "@example.com",
                "其他用户",
                "test-password-hash"));

        KnowledgeBase knowledgeBase = knowledgeBaseService.create(
                owner.getId(),
                "归属隔离测试知识库",
                "只允许所有者访问");

        KnowledgeBase owned = knowledgeBaseService.requireOwnedBy(
                knowledgeBase.getId(),
                owner.getId());

        assertThat(owned.getOwnerId()).isEqualTo(owner.getId());
        assertThat(knowledgeBaseService.findOwnedBy(owner.getId()))
                .extracting(KnowledgeBase::getId)
                .containsExactly(knowledgeBase.getId());
        assertThat(knowledgeBaseService.findOwnedBy(anotherUser.getId()))
                .isEmpty();

        assertThatThrownBy(() -> knowledgeBaseService.requireOwnedBy(
                knowledgeBase.getId(),
                anotherUser.getId()))
                .isInstanceOf(KnowledgeBaseNotFoundException.class);
    }
}
