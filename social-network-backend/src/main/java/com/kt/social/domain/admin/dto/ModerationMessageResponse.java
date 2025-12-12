package com.kt.social.domain.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModerationMessageResponse {
    private String id; // ID tin nhắn (UUID)
    private Long conversationId;

    private Long senderId;
    private String senderName;
    private String senderAvatar;

    private String content;
    private String sentAt; // Timestamp trong JSON thường là String
}