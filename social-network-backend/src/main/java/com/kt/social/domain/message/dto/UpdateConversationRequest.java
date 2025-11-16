package com.kt.social.domain.message.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateConversationRequest {
    private Long conversationId;
    private String title;
    private MultipartFile mediaFile;
}
