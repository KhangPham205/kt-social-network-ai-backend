package com.kt.social.domain.message.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ConversationSummaryResponse {
    private Long id;
    private String title;
    private String mediaUrl;
    private boolean isGroup;

    private Map<String, Object> lastMessage;
    private List<ParticipantDto> participants;

    private Instant updatedAt;
}
