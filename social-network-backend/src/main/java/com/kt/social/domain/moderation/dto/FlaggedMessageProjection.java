package com.kt.social.domain.moderation.dto;

import java.time.Instant;

public interface FlaggedMessageProjection {
    String getId();
    Long getConversationId();
    Long getSenderId();
    String getSenderName();
    String getSenderAvatar();
    String getContent();
    String getSentAt();
    Instant getDeletedAt();
    Object getMedia();
}
