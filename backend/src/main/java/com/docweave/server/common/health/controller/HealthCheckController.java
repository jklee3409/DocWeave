package com.docweave.server.common.health.controller;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthCheckController {

    private final ChatModel chatModel;

    public HealthCheckController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

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