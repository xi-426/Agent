package com.yan.agent.chat;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.List;

@Service
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;

    public ChatSessionService(
            ChatSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public ChatSession create(
            Long userId,
            String title) {
        ChatSession session = new ChatSession(
                userId,
                title);

        return sessionRepository.save(session);
    }

    public ChatSession requireOwnedBy(
            Long sessionId,
            Long userId) {

        return sessionRepository.findByIdAndUserId(
                sessionId,
                userId)
                .orElseThrow(() -> new ChatSessionNotFoundException(
                        sessionId));
    }

    public ChatSession requireById(Long sessionId) {
        // 查询会话；找不到时抛出 ChatSessionNotFoundException。
        Optional<ChatSession> session = sessionRepository.findById(
                sessionId);

        if (session.isEmpty()) {
            throw new ChatSessionNotFoundException(
                    sessionId);
        }

        return session.get();
    }

    public List<ChatSession> findOwnedBy(Long userId) {
        return sessionRepository.findByUserIdOrderByIdDesc(userId);
    }
}
