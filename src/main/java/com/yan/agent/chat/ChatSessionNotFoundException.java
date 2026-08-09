package com.yan.agent.chat;

public class ChatSessionNotFoundException
        extends RuntimeException {

    public ChatSessionNotFoundException(Long sessionId) {
        super("聊天会话不存在：" + sessionId);
    }
}
