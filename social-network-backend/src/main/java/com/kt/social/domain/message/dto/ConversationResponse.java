package com.kt.social.domain.message.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ConversationResponse {
    private Long id;
    private Boolean isGroup;
    private String title;
    private String mediaUrl;
    private Instant createdAt;
    private List<Long> memberIds;
}