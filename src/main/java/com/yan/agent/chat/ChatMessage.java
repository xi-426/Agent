package com.yan.agent.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_role", nullable = false, length = 20)
    private ChatMemoryMessage.Role role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    protected ChatMessage() {
    }

    public ChatMessage(
            Long sessionId,
            ChatMemoryMessage.Role role,
            String content) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public ChatMemoryMessage.Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
