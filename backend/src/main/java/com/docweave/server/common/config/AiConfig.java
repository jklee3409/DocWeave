package com.docweave.server.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("당신은 'DocWeave' 라는 유능한 AI 비서입니다. 한국어로 친절하게 대답해주세요.")
                .build();
    }
}
