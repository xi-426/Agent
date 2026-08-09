package com.yan.agent.chat.dto;

import com.yan.agent.chat.ChatMemoryMessage;

public record ChatMessageResponse(
        Long id,
        ChatMemoryMessage.Role role,
        String content) {
}
