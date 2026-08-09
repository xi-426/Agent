package com.yan.agent.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop20BySessionIdOrderByIdDesc(
            Long sessionId);
}
