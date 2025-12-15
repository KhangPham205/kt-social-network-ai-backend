package com.kt.social.domain.moderation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

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

    private List<String> mediaUrls;

    private Instant deletedAt;

    private long reportCount;
    private long complaintCount;
}