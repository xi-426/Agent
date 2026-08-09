package com.yan.agent.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository
        extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByIdAndUserId(
            Long id,
            Long userId);

    List<ChatSession> findByUserIdOrderByIdDesc(Long userId);
}
