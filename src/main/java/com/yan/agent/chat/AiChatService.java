package com.yan.agent.chat;

import com.yan.agent.common.AiConfigurationException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {

    private static final String PLACEHOLDER_API_KEY = "demo-key-not-configured";

    private final ChatClient chatClient;
    private final String apiKey;

    public AiChatService(
            ChatClient chatClient,
            @Value("${spring.ai.openai.api-key}") String apiKey) {
        this.chatClient = chatClient;
        this.apiKey = apiKey;
    }

    public String chat(String userMessage) {
        ensureApiKeyConfigured();

        ChatClient.ChatClientRequestSpec request = chatClient
                .prompt()
                .user(userMessage);

        ChatClient.CallResponseSpec response = request.call();
        return response.content();
    }

    private void ensureApiKeyConfigured() {
        if (apiKey.isBlank() || PLACEHOLDER_API_KEY.equals(apiKey)) {
            throw new AiConfigurationException(
                    "尚未配置DEEPSEEK_API_KEY，请先阅读README中的启动步骤");
        }
    }
}
