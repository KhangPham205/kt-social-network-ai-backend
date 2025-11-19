package com.kt.social.domain.message.dto;

import lombok.Data;

@Data
public class MarkReadRequest {
    private Long conversationId;
    private String messageId;
}
