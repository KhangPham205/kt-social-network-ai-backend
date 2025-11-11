package com.kt.social.domain.message.dto;

import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRequest {
    private Long conversationId;
    private String content;
    private Long replyToId;

    // Có thể gửi nhiều file
    private List<MultipartFile> mediaFiles;
}