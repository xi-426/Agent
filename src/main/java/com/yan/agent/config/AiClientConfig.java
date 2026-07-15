package com.yan.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        String systemMessage = """
                你是企业知识库智能助手。
                当前是项目第一阶段，还没有接入企业知识库和业务工具。
                请使用简洁、准确的中文回答，不确定时明确说明不确定。
                """;

        return builder
                .defaultSystem(systemMessage)
                .build();
    }
}

