package com.docweave.server.common.health.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class HealthCheckController {

    private final OpenAiChatModel chatModel;

    @GetMapping("/health")
    public Map<String, String> healthCheck() {
        return Map.of("status", "ok", "message", "DocWeave API is running");
    }

    @GetMapping("/ai-test")
    public Map<String, String> aiTest() {
        String response = chatModel.call("Hello! Are you ready?");
        return Map.of("ai_response", response);
    }
}