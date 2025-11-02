package com.kt.social.domain.message.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRequest {
    private Long conversationId;
    private Long replyId;
    private String content;
    private String mediaUrl;
}