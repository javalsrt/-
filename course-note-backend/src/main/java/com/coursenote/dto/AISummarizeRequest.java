package com.coursenote.dto;

import lombok.Data;

import java.util.List;

@Data
public class AISummarizeRequest {
    private Long sessionId;
    private Long courseId;
    private List<Long> materialIds;
    private String customPrompt;
}
