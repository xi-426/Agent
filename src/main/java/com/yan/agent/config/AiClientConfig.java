package com.yan.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        String systemMessage = """
            你是“知屿”个人知识库智能助手。
                请使用简洁、准确的中文回答，不确定时明确说明不确定。
                当用户消息提供了知识库资料和回答规则时，必须严格遵守。
                """;

        return builder
                .defaultSystem(systemMessage)
                .build();
    }
}
