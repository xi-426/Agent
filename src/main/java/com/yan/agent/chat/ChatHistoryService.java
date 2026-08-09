package com.yan.agent.chat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatHistoryService {

    private final ChatMessageRepository messageRepository;

    public ChatHistoryService(
            ChatMessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public ChatMessage saveMessage(
            Long sessionId,
            ChatMemoryMessage.Role role,
            String content) {
        ChatMessage message = new ChatMessage(
                sessionId,
                role,
                content);

        return messageRepository.save(message);
    }

    @Transactional
    public void saveTurn(
            Long sessionId,
            String userMessage,
            String assistantMessage) {
        saveMessage(
                sessionId,
                ChatMemoryMessage.Role.USER,
                userMessage);

        saveMessage(
                sessionId,
                ChatMemoryMessage.Role.ASSISTANT,
                assistantMessage);
    }

    public List<ChatMemoryMessage> loadRecentMessages(
            Long sessionId) {
        //查询数据库最新20条，反转为旧到新，再转换成 ChatMemoryMessage。
        List<ChatMessage> databaseMessages = new ArrayList<>(
                messageRepository
                        .findTop20BySessionIdOrderByIdDesc(
                                sessionId));

        Collections.reverse(databaseMessages);

        List<ChatMemoryMessage> memoryMessages = new ArrayList<>();

        for (ChatMessage databaseMessage : databaseMessages) {

            ChatMemoryMessage memoryMessage = new ChatMemoryMessage(
                    databaseMessage.getRole(),
                    databaseMessage.getContent());

            memoryMessages.add(memoryMessage);
        }

        return memoryMessages;
    }

    public List<ChatMessage> loadRecentChatMessages(Long sessionId) {
        List<ChatMessage> messages = new ArrayList<>(
                messageRepository.findTop20BySessionIdOrderByIdDesc(
                        sessionId));
        Collections.reverse(messages);
        return messages;
    }
}
