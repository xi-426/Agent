package com.yan.agent.chat;

public class ChatMemoryMessage {

    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }

    private final Role role;
    private final String content;

    public ChatMemoryMessage(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
