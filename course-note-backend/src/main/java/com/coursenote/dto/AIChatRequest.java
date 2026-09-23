package com.coursenote.dto;

import lombok.Data;

import java.util.List;

@Data
public class AIChatRequest {
    private Long userId;
    private Long courseId;
    private Long sessionId;
    private String message;
    private List<ChatMessage> history;
    private Boolean useKnowledgeBase = true;

    @Data
    public static class ChatMessage {
        private String role;
        private String content;
    }
}
