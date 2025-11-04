package com.kt.social.domain.message.dto;

import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRequest {
    private Long conversationId;
    private Long replyToId;
    private String content;
    private String mediaUrl; // nếu gửi link (VD: ảnh từ cloud)
    private MultipartFile mediaFile; // nếu upload ảnh trực tiếp
}