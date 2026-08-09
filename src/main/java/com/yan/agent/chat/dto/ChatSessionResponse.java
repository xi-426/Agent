package com.yan.agent.chat.dto;

public class ChatSessionResponse {

    private final Long id;
    private final Long userId;
    private final String title;

    public ChatSessionResponse(
            Long id,
            Long userId,
            String title) {
        this.id = id;
        this.userId = userId;
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }
}
