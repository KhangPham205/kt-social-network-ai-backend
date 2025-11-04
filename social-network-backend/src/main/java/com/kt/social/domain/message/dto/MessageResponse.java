package com.kt.social.domain.message.dto;

import lombok.*;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {
    private Long id;
    private Long conversationId;

    private Long senderId;
    private String senderName;
    private String senderAvatar;

    private Long replyToId;
    private String content;
    private String mediaUrl;

    private Instant createdAt;
    private Boolean isRead;
}